package indexer

import (
	"context"
	"fmt"
	"html"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"cinesubz-backend/internal/model"
)

var (
	reItemCard     = regexp.MustCompile(`(?s)<div\s+id=["']item-(\d+)["'][^>]*class=["'][^"']*display-item[^"']*["'][^>]*>(.*?)</div>\s*</div>`)
	reLinkTitle    = regexp.MustCompile(`(?s)<a\s+[^>]*href=["']([^"']+)["'][^>]*title=["']([^"']+)["']`)
	reImage        = regexp.MustCompile(`(?s)<img\s+[^>]*(?:data-original|src)=["']([^"']+)["']`)
	reYear         = regexp.MustCompile(`\((\d{4})\)`)
	reIMDb         = regexp.MustCompile(`IMDbID\s+([a-zA-Z0-9]+)`)
	reQuality      = regexp.MustCompile(`(?s)<span\s+class=["']mli-quality["']>([^<]+)</span>`)
	reGenresPage   = regexp.MustCompile(`href=["']https://cinesubz\.(?:co|lk|net)/genre/([^/'"]+)/["']`)
	reDescMeta     = regexp.MustCompile(`(?s)<meta\s+property=["']og:description["']\s+content=["']([^"']+)["']`)
	reTitleMeta    = regexp.MustCompile(`(?is)<meta\s+property=['"]og:title['"]\s+content=['"]([^'"]+)['"]`)
	reImageMeta    = regexp.MustCompile(`(?is)<meta\s+property=['"]og:image['"]\s+content=['"]([^'"]+)['"]`)
	reOgURL        = regexp.MustCompile(`(?is)<meta\s+property=['"]og:url['"]\s+content=['"]([^'"]+)['"]`)
	reCanonical    = regexp.MustCompile(`(?is)<link\s+[^>]*rel=['"]canonical['"][^>]*href=['"]([^'"]+)['"]`)
	rePostID       = regexp.MustCompile(`data-post=['"](\d+)['"]|id=['"]item-(\d+)['"]`)
	reSeasonBlocks = regexp.MustCompile(`(?is)<ul[^>]+id=['"]season-listep-(\d+)['"][^>]*>(.*?)</ul>`)
	reEpisodeItem  = regexp.MustCompile(`(?is)<li[^>]*>(.*?)</li>`)
	reDataPID      = regexp.MustCompile(`data-pid=['"](\d+)['"]`)
	reDataEpNum    = regexp.MustCompile(`data-episode=['"](\d+)['"]`)
	reEpLink       = regexp.MustCompile(`href=['"]([^'"]+)['"]`)
	reEpTitle      = regexp.MustCompile(`(?is)class=['"]ep-title['"][^>]*>(.*?)</span>`)
	reEpDate       = regexp.MustCompile(`(?is)class=['"]ep-date['"][^>]*>(.*?)</span>`)
	reEpThumb      = regexp.MustCompile(`(?i)<img[^>]+src=['"]([^'"]+)['"]`)
)

type Scraper struct {
	client *http.Client
}

func NewScraper() *Scraper {
	transport := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			var d net.Dialer
			return d.DialContext(ctx, "tcp4", addr)
		},
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   5 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	return &Scraper{
		client: &http.Client{
			Transport: transport,
			Timeout:   15 * time.Second,
		},
	}
}

func (s *Scraper) get(targetURL string) (string, error) {
	req, err := http.NewRequest("GET", targetURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Referer", "https://cinesubz.co/")

	resp, err := s.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("HTTP error %d from %s", resp.StatusCode, targetURL)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	return string(body), nil
}

// ScrapeCatalogPage scrapes movies from https://cinesubz.co/movies/page/{page}/
func (s *Scraper) ScrapeCatalogPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.co/movies/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.co/movies/page/%d/", page)
	}

	html, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	return s.parseMovieCards(html)
}

// ScrapeTrendingPage queries CineSubz trending movies
func (s *Scraper) ScrapeTrendingPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.co/trending/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.co/trending/page/%d/", page)
	}

	content, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	return s.parseMovieCards(content)
}

// ScrapeTVShowsPage queries CineSubz TV shows & series
func (s *Scraper) ScrapeTVShowsPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.co/tvshows/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.co/tvshows/page/%d/", page)
	}

	content, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	movies, err := s.parseMovieCards(content)
	if err == nil {
		for i := range movies {
			movies[i].IsTVShow = true
			movies[i].Genres = append(movies[i].Genres, "tvshows")
		}
	}
	return movies, err
}

// ScrapeSearch queries CineSubz search and parses result cards
func (s *Scraper) ScrapeSearch(query string) ([]model.Movie, error) {
	searchURL := fmt.Sprintf("https://cinesubz.co/?s=%s", url.QueryEscape(query))
	html, err := s.get(searchURL)
	if err != nil {
		return nil, err
	}

	return s.parseMovieCards(html)
}

// ScrapeMovieDetails fetches full metadata (synopsis, all genres) from a single movie page
func (s *Scraper) ScrapeMovieDetails(pageURL string, existing *model.Movie) (*model.Movie, error) {
	html, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	m := existing
	if m == nil {
		m = &model.Movie{PageURL: pageURL}
	}

	// Description
	if descMatch := reDescMeta.FindStringSubmatch(html); len(descMatch) > 1 {
		m.Description = strings.TrimSpace(descMatch[1])
	}

	// Genres
	genreMatches := reGenresPage.FindAllStringSubmatch(html, -1)
	genreMap := make(map[string]bool)
	for _, gm := range genreMatches {
		if len(gm) > 1 {
			g := strings.ToLower(strings.TrimSpace(gm[1]))
			if g != "" && !genreMap[g] {
				genreMap[g] = true
				m.Genres = append(m.Genres, g)
			}
		}
	}

	// IMDb
	if imdbMatch := reIMDb.FindStringSubmatch(html); len(imdbMatch) > 1 {
		m.IMDb = imdbMatch[1]
	}

	return m, nil
}

// parseMovieCards extracts movies from catalog/search card HTML
func (s *Scraper) parseMovieCards(content string) ([]model.Movie, error) {
	matches := reItemCard.FindAllStringSubmatch(content, -1)
	movies := make([]model.Movie, 0, len(matches))

	for _, m := range matches {
		if len(m) < 3 {
			continue
		}
		postID := m[1]
		cardContent := m[2]

		var pageURL, rawTitle string
		if linkMatch := reLinkTitle.FindStringSubmatch(cardContent); len(linkMatch) > 2 {
			pageURL = linkMatch[1]
			rawTitle = linkMatch[2]
		}

		var poster string
		if imgMatch := reImage.FindStringSubmatch(cardContent); len(imgMatch) > 1 {
			poster = imgMatch[1]
		}

		var year string
		if yearMatch := reYear.FindStringSubmatch(rawTitle); len(yearMatch) > 1 {
			year = yearMatch[1]
		}

		var imdb string
		if imdbMatch := reIMDb.FindStringSubmatch(cardContent); len(imdbMatch) > 1 {
			imdb = imdbMatch[1]
		}

		var quality string
		if qMatch := reQuality.FindStringSubmatch(cardContent); len(qMatch) > 1 {
			quality = strings.TrimSpace(qMatch[1])
		}

		// Clean up title: Decode HTML entities and strip "Sinhala Subtitle(s) | ..."
		rawTitle = html.UnescapeString(rawTitle)
		cleanedTitle := rawTitle
		if idx := strings.Index(cleanedTitle, "Sinhala Subtitle"); idx != -1 {
			cleanedTitle = strings.TrimSpace(cleanedTitle[:idx])
		}
		cleanedTitle = strings.TrimSuffix(cleanedTitle, "|")
		cleanedTitle = strings.TrimSpace(cleanedTitle)

		isTV := strings.Contains(pageURL, "/tvshows/") ||
			strings.Contains(rawTitle, "TV Series") ||
			strings.Contains(rawTitle, "S01") ||
			strings.Contains(rawTitle, "Season")

		movie := model.Movie{
			ID:          postID,
			Title:       cleanedTitle,
			Poster:      poster,
			Backdrop:    poster,
			Description: rawTitle,
			Year:        year,
			IMDb:        imdb,
			Rating:      quality,
			Genres:      []string{"sinhala"},
			PageURL:     pageURL,
			IsTVShow:    isTV,
			Servers: []model.ServerOption{
				{Type: "mv", Number: "1", Name: "CS Player"},
				{Type: "mv", Number: "2", Name: "Evo Player"},
				{Type: "mv", Number: "trailer", Name: "Trailer"},
			},
			IndexedAt: time.Now(),
		}

		if isTV {
			movie.Genres = append(movie.Genres, "tvshows")
		}

		// Infer basic genre hints from quality/year
		if year == fmt.Sprintf("%d", time.Now().Year()) {
			movie.Genres = append(movie.Genres, "latest")
		}

		movies = append(movies, movie)
	}

	return movies, nil
}

// ScrapeSeriesDetails fetches full TV series metadata, seasons, and episodes from its page URL.
func (s *Scraper) ScrapeSeriesDetails(pageURL string) (*model.SeriesDetail, error) {
	pageHTML, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	detail := &model.SeriesDetail{
		PageURL:   pageURL,
		Seasons:   make([]model.Season, 0),
		UpdatedAt: time.Now(),
	}

	if m := reOgURL.FindStringSubmatch(pageHTML); len(m) > 1 && m[1] != "" {
		detail.PageURL = m[1]
	} else if m := reCanonical.FindStringSubmatch(pageHTML); len(m) > 1 && m[1] != "" {
		detail.PageURL = m[1]
	}

	if m := reTitleMeta.FindStringSubmatch(pageHTML); len(m) > 1 {
		t := html.UnescapeString(m[1])
		if idx := strings.Index(t, "Sinhala Subtitle"); idx != -1 {
			t = strings.TrimSpace(t[:idx])
		}
		t = strings.TrimSuffix(t, "|")
		detail.Title = strings.TrimSpace(t)
	}
	if m := reDescMeta.FindStringSubmatch(pageHTML); len(m) > 1 {
		detail.Description = html.UnescapeString(m[1])
	}
	if m := reImageMeta.FindStringSubmatch(pageHTML); len(m) > 1 {
		detail.Poster = m[1]
		detail.Backdrop = m[1]
	}
	if m := reYear.FindStringSubmatch(detail.Title); len(m) > 1 {
		detail.Year = m[1]
	}
	if m := reIMDb.FindStringSubmatch(pageHTML); len(m) > 1 {
		detail.IMDb = m[1]
	}
	if m := rePostID.FindStringSubmatch(pageHTML); len(m) > 1 {
		for i := 1; i < len(m); i++ {
			if m[i] != "" {
				detail.ID = m[i]
				break
			}
		}
	}

	// Extract genres
	genreMatches := reGenresPage.FindAllStringSubmatch(pageHTML, -1)
	genreMap := make(map[string]bool)
	for _, gm := range genreMatches {
		if len(gm) > 1 {
			g := strings.ToLower(strings.TrimSpace(gm[1]))
			if g != "" && !genreMap[g] {
				genreMap[g] = true
				detail.Genres = append(detail.Genres, g)
			}
		}
	}

	blocks := reSeasonBlocks.FindAllStringSubmatch(pageHTML, -1)
	for _, block := range blocks {
		sNum, _ := strconv.Atoi(block[1])
		season := model.Season{
			SeasonNumber: sNum,
			Title:        fmt.Sprintf("Season %02d", sNum),
			Episodes:     make([]model.Episode, 0),
		}

		items := reEpisodeItem.FindAllStringSubmatch(block[2], -1)
		for _, it := range items {
			itemHTML := it[1]
			pidMatch := reDataPID.FindStringSubmatch(itemHTML)
			if len(pidMatch) < 2 {
				continue
			}
			pid := pidMatch[1]

			epNum := 1
			if em := reDataEpNum.FindStringSubmatch(itemHTML); len(em) > 1 {
				epNum, _ = strconv.Atoi(em[1])
			}

			epLink := ""
			if lm := reEpLink.FindStringSubmatch(itemHTML); len(lm) > 1 {
				epLink = lm[1]
			}

			epTitle := fmt.Sprintf("Episode %d", epNum)
			if tm := reEpTitle.FindStringSubmatch(itemHTML); len(tm) > 1 {
				epTitle = html.UnescapeString(strings.TrimSpace(tm[1]))
			}

			epDate := ""
			if dm := reEpDate.FindStringSubmatch(itemHTML); len(dm) > 1 {
				epDate = html.UnescapeString(strings.TrimSpace(dm[1]))
			}

			epThumb := detail.Backdrop
			if thm := reEpThumb.FindStringSubmatch(itemHTML); len(thm) > 1 {
				if !strings.Contains(thm[1], "no/zt_backdrop") {
					epThumb = thm[1]
				}
			}

			season.Episodes = append(season.Episodes, model.Episode{
				ID:            pid,
				EpisodeNumber: epNum,
				Title:         epTitle,
				Date:          epDate,
				Thumbnail:     epThumb,
				PageURL:       epLink,
			})
		}

		detail.Seasons = append(detail.Seasons, season)
	}

	return detail, nil
}
