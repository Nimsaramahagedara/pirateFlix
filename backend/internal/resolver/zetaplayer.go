package resolver

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"cinesubz-backend/internal/model"
)

var (
	reDefQuality  = regexp.MustCompile(`\{[^{}]*?"url"\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["'][^{}]*?"default"\s*:\s*true`)
	reArtURL      = regexp.MustCompile(`url\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']`)
	reDirectMedia = regexp.MustCompile(`(https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)`)
	reIframeSrc   = regexp.MustCompile(`(?i)<iframe[^>]+src=['"]([^'"]+)['"]`)
)

type zetaPlayerRawResponse struct {
	EmbedURL string `json:"embed_url"`
	Type     any    `json:"type"` // can be string "mp4" or bool false
	PlayURL  string `json:"play_url"`
	Msg      string `json:"msg"`
}

type StreamResolver struct {
	client *http.Client
}

func NewStreamResolver() *StreamResolver {
	return &StreamResolver{
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// extractDirectVideoURL parses player HTML pages (e.g. Artplayer / CS Player) to extract the actual video CDN URL.
func (r *StreamResolver) extractDirectVideoURL(pageURL string) string {
	if strings.Contains(pageURL, "skylines") && strings.Contains(pageURL, ".mp4") {
		return pageURL
	}

	req, err := http.NewRequest("GET", pageURL, nil)
	if err != nil {
		return pageURL
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Referer", "https://cinesubz.lk/")

	resp, err := r.client.Do(req)
	if err != nil {
		return pageURL
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return pageURL
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return pageURL
	}
	html := string(body)

	// 1. Check for "default":true in ALL_QUALITIES array
	if m := reDefQuality.FindStringSubmatch(html); len(m) > 1 {
		return m[1]
	}

	// 2. Check for Artplayer main video url: '...'
	if m := reArtURL.FindStringSubmatch(html); len(m) > 1 {
		return m[1]
	}

	// 3. Fallback to any direct media link
	if m := reDirectMedia.FindStringSubmatch(html); len(m) > 1 {
		return m[1]
	}

	return pageURL
}

// ResolveStream queries the ZetaPlayer API / admin-ajax and extracts the raw playable video URL for ExoPlayer.
func (r *StreamResolver) ResolveStream(postID string, serverNum string, streamType string) (*model.StreamResponse, error) {
	if postID == "" {
		return nil, fmt.Errorf("postID cannot be empty")
	}
	if serverNum == "" {
		serverNum = "1"
	}

	// 1. If explicitly requested as episode ("ep"), resolve via admin-ajax directly
	if streamType == "ep" {
		return r.resolveViaAdminAjax(postID, serverNum, "ep")
	}

	// 2. Otherwise try wp-json zetaplayer endpoint for movies
	domains := []string{"https://cinesubz.lk", "https://cinesubz.net"}
	var lastErr error

	for _, domain := range domains {
		apiURL := fmt.Sprintf("%s/wp-json/zetaplayer/v2/%s/mv/%s", domain, postID, serverNum)
		req, err := http.NewRequest("GET", apiURL, nil)
		if err != nil {
			lastErr = err
			continue
		}

		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
		req.Header.Set("Referer", "https://cinesubz.lk/")

		resp, err := r.client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			lastErr = fmt.Errorf("API returned HTTP %d", resp.StatusCode)
			continue
		}

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			lastErr = err
			continue
		}

		var raw zetaPlayerRawResponse
		if err := json.Unmarshal(body, &raw); err != nil {
			lastErr = err
			continue
		}

		embedURL := raw.EmbedURL
		if embedURL == "" {
			embedURL = raw.PlayURL
		}

		if embedURL != "" {
			directVideoURL := r.extractDirectVideoURL(embedURL)
			stType := "mp4"
			if strings.Contains(directVideoURL, ".m3u8") {
				stType = "hls"
			}

			return &model.StreamResponse{
				PostID:    postID,
				Server:    serverNum,
				StreamURL: directVideoURL,
				EmbedURL:  embedURL,
				Type:      stType,
				Headers: map[string]string{
					"Referer":    "https://cinesubz.lk",
					"User-Agent": "Mozilla/5.0 (Linux; Android TV)",
				},
			}, nil
		}
	}

	// 3. Fallback to admin-ajax for movies ("mv")
	if res, err := r.resolveViaAdminAjax(postID, serverNum, "mv"); err == nil {
		return res, nil
	}

	// 4. Fallback to admin-ajax for episodes ("ep") in case it's an episode ID
	if res, err := r.resolveViaAdminAjax(postID, serverNum, "ep"); err == nil {
		return res, nil
	}

	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("failed to resolve stream for post ID %s", postID)
}

func (r *StreamResolver) resolveViaAdminAjax(postID string, serverNum string, targetType string) (*model.StreamResponse, error) {
	domains := []string{"https://cinesubz.net", "https://cinesubz.lk"}
	var lastErr error

	for _, domain := range domains {
		ajaxURL := fmt.Sprintf("%s/wp-admin/admin-ajax.php", domain)
		data := url.Values{}
		data.Set("action", "zeta_player_ajax")
		data.Set("post", postID)
		data.Set("nume", serverNum)
		data.Set("type", targetType)

		req, err := http.NewRequest("POST", ajaxURL, strings.NewReader(data.Encode()))
		if err != nil {
			lastErr = err
			continue
		}

		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
		req.Header.Set("Referer", domain+"/")
		req.Header.Set("X-Requested-With", "XMLHttpRequest")
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

		resp, err := r.client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		defer resp.Body.Close()

		if resp.StatusCode != http.StatusOK {
			lastErr = fmt.Errorf("admin-ajax (%s) returned HTTP %d", domain, resp.StatusCode)
			continue
		}

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			lastErr = err
			continue
		}

		var raw zetaPlayerRawResponse
		if err := json.Unmarshal(body, &raw); err != nil {
			lastErr = err
			continue
		}

		embedURL := raw.EmbedURL
		if embedURL == "" {
			embedURL = raw.PlayURL
		}

		if embedURL == "" {
			lastErr = fmt.Errorf("no stream URL returned by admin-ajax (%s)", domain)
			continue
		}

		// If it returned an iframe HTML string, extract iframe src
		if strings.Contains(embedURL, "<iframe") {
			if m := reIframeSrc.FindStringSubmatch(embedURL); len(m) > 1 {
				embedURL = m[1]
			}
		}

		directVideoURL := r.extractDirectVideoURL(embedURL)
		stType := "mp4"
		if strings.Contains(directVideoURL, ".m3u8") {
			stType = "hls"
		}

		return &model.StreamResponse{
			PostID:    postID,
			Server:    serverNum,
			StreamURL: directVideoURL,
			EmbedURL:  embedURL,
			Type:      stType,
			Headers: map[string]string{
				"Referer":    "https://cinesubz.lk",
				"User-Agent": "Mozilla/5.0 (Linux; Android TV)",
			},
		}, nil
	}

	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("no stream URL returned by admin-ajax")
}
