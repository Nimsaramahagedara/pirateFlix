package model

import "time"

// ServerOption represents an available streaming server option for a movie.
type ServerOption struct {
	Type   string `json:"type"`   // "mv", etc.
	Number string `json:"number"` // "1", "2", "trailer"
	Name   string `json:"name"`   // "CS Player", "Evo Player", "Youtube"
}

// Movie represents a catalog item ready for Netflix-style display.
type Movie struct {
	ID          string         `json:"id"`
	Title       string         `json:"title"`
	Poster      string         `json:"poster"`
	Backdrop    string         `json:"backdrop,omitempty"`
	Description string         `json:"description"`
	Year        string         `json:"year,omitempty"`
	IMDb        string         `json:"imdb,omitempty"`
	Rating      string         `json:"rating,omitempty"`
	Genres      []string       `json:"genres"`
	PageURL     string         `json:"page_url"`
	Servers     []ServerOption `json:"servers,omitempty"`
	IndexedAt   time.Time      `json:"indexed_at"`
}

// StreamResponse provides the resolved direct streaming URL and player headers.
type StreamResponse struct {
	PostID    string            `json:"post_id"`
	Server    string            `json:"server"`
	StreamURL string            `json:"stream_url"`
	Type      string            `json:"type"` // "mp4", "iframe", etc.
	Headers   map[string]string `json:"headers"`
	Error     string            `json:"error,omitempty"`
}

// CategoryRow represents a horizontal carousel row on Android TV.
type CategoryRow struct {
	Title    string  `json:"title"`
	Category string  `json:"category"`
	Movies   []Movie `json:"movies"`
}

// HomeFeed represents the complete Netflix home page response.
type HomeFeed struct {
	HeroBanner *Movie        `json:"hero_banner,omitempty"`
	Rows       []CategoryRow `json:"rows"`
}

// MovieListResponse represents a paginated movie list.
type MovieListResponse struct {
	Total  int     `json:"total"`
	Page   int     `json:"page"`
	Limit  int     `json:"limit"`
	Movies []Movie `json:"movies"`
}
