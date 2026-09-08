package indexer

import (
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"cinesubz-backend/internal/model"
)

var (
	reItemCard   = regexp.MustCompile(`(?s)<div\s+id=["']item-(\d+)["'][^>]*class=["'][^"']*display-item[^"']*["'][^>]*>(.*?)</div>\s*</div>`)
	reLinkTitle  = regexp.MustCompile(`(?s)<a\s+[^>]*href=["']([^"']+)["'][^>]*title=["']([^"']+)["']`)
	reImage      = regexp.MustCompile(`(?s)<img\s+[^>]*(?:data-original|src)=["']([^"']+)["']`)
	reYear       = regexp.MustCompile(`\((\d{4})\)`)
	reIMDb       = regexp.MustCompile(`IMDbID\s+([a-zA-Z0-9]+)`)
	reQuality    = regexp.MustCompile(`(?s)<span\s+class=["']mli-quality["']>([^<]+)</span>`)
	reGenresPage = regexp.MustCompile(`href=["']https://cinesubz\.lk/genre/([^/'"]+)/["']`)
	reDescMeta   = regexp.MustCompile(`(?s)<meta\s+property=["']og:description["']\s+content=["']([^"']+)["']`)
)

type Scraper struct {
	client *http.Client
}

func NewScraper() *Scraper {
	return &Scraper{
		client: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

func (s *Scraper) get(targetURL string) (string, error) {
	req, err := http.NewRequest("GET", targetURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Referer", "https://cinesubz.lk/")

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

// ScrapeCatalogPage scrapes movies from https://cinesubz.lk/movies/page/{page}/
func (s *Scraper) ScrapeCatalogPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.lk/movies/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.lk/movies/page/%d/", page)
	}

	html, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	return s.parseMovieCards(html)
}

// ScrapeTrendingPage queries CineSubz trending movies
func (s *Scraper) ScrapeTrendingPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.lk/trending/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.lk/trending/page/%d/", page)
	}

	content, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	return s.parseMovieCards(content)
}

// ScrapeTVShowsPage queries CineSubz TV shows & series
func (s *Scraper) ScrapeTVShowsPage(page int) ([]model.Movie, error) {
	pageURL := "https://cinesubz.lk/tvshows/"
	if page > 1 {
		pageURL = fmt.Sprintf("https://cinesubz.lk/tvshows/page/%d/", page)
	}

	content, err := s.get(pageURL)
	if err != nil {
		return nil, err
	}

	movies, err := s.parseMovieCards(content)
	if err == nil {
		for i := range movies {
			movies[i].Genres = append(movies[i].Genres, "tvshows")
		}
	}
	return movies, err
}

// ScrapeSearch queries CineSubz search and parses result cards
func (s *Scraper) ScrapeSearch(query string) ([]model.Movie, error) {
	searchURL := fmt.Sprintf("https://cinesubz.lk/?s=%s", url.QueryEscape(query))
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
			Servers: []model.ServerOption{
				{Type: "mv", Number: "1", Name: "CS Player"},
				{Type: "mv", Number: "2", Name: "Evo Player"},
				{Type: "mv", Number: "trailer", Name: "Trailer"},
			},
			IndexedAt: time.Now(),
		}

		// Infer basic genre hints from quality/year
		if year == fmt.Sprintf("%d", time.Now().Year()) {
			movie.Genres = append(movie.Genres, "latest")
		}

		movies = append(movies, movie)
	}

	return movies, nil
}
