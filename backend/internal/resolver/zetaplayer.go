package resolver

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"cinesubz-backend/internal/model"
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

// ResolveStream queries the ZetaPlayer API to fetch the direct streaming source for a movie.
func (r *StreamResolver) ResolveStream(postID string, serverNum string) (*model.StreamResponse, error) {
	if postID == "" {
		return nil, fmt.Errorf("postID cannot be empty")
	}
	if serverNum == "" {
		serverNum = "1"
	}

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

		streamURL := raw.EmbedURL
		if streamURL == "" {
			streamURL = raw.PlayURL
		}

		if streamURL == "" {
			lastErr = fmt.Errorf("no stream URL found in player response")
			continue
		}

		streamType := "mp4"
		if strType, ok := raw.Type.(string); ok && strType != "" {
			streamType = strType
		}

		return &model.StreamResponse{
			PostID:    postID,
			Server:    serverNum,
			StreamURL: streamURL,
			Type:      streamType,
			Headers: map[string]string{
				"Referer":    "https://cinesubz.lk",
				"User-Agent": "Mozilla/5.0 (Linux; Android TV)",
			},
		}, nil
	}

	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("failed to resolve stream for post ID %s", postID)
}
