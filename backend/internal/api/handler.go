package api

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"time"

	"cinesubz-backend/internal/indexer"
	"cinesubz-backend/internal/model"
	"cinesubz-backend/internal/resolver"
)

type Handler struct {
	store     *indexer.Store
	scraper   *indexer.Scraper
	resolver  *resolver.StreamResolver
	startTime time.Time
}

func NewHandler(store *indexer.Store, scraper *indexer.Scraper, res *resolver.StreamResolver) *Handler {
	return &Handler{
		store:     store,
		scraper:   scraper,
		resolver:  res,
		startTime: time.Now(),
	}
}

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// HandleHealth returns server health status and index stats
func (h *Handler) HandleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":         "ok",
		"indexed_movies": h.store.Count(),
		"uptime_seconds": int(time.Since(h.startTime).Seconds()),
	})
}

// HandleHome returns the Netflix-style carousels and hero banner for Android TV
func (h *Handler) HandleHome(w http.ResponseWriter, r *http.Request) {
	feed := h.store.GetHomeFeed()
	writeJSON(w, http.StatusOK, feed)
}

// HandleMovies returns a paginated list of movies with optional genre filter
func (h *Handler) HandleMovies(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	page, _ := strconv.Atoi(q.Get("page"))
	limit, _ := strconv.Atoi(q.Get("limit"))
	genre := q.Get("genre")

	if page <= 0 {
		page = 1
	}
	if limit <= 0 || limit > 100 {
		limit = 20
	}

	movies, total := h.store.GetAll(page, limit, genre)
	writeJSON(w, http.StatusOK, model.MovieListResponse{
		Total:  total,
		Page:   page,
		Limit:  limit,
		Movies: movies,
	})
}

// HandleMovieByID returns full details for a specific movie
func (h *Handler) HandleMovieByID(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "movie id is required"})
		return
	}

	movie, found := h.store.GetByID(id)
	if !found {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "movie not found"})
		return
	}

	// If full description or genres are sparse, fetch details on the fly
	if movie.Description == "" || len(movie.Genres) <= 1 {
		if detailed, err := h.scraper.ScrapeMovieDetails(movie.PageURL, movie); err == nil {
			h.store.Upsert(detailed)
			movie = detailed
		}
	}

	writeJSON(w, http.StatusOK, movie)
}

// HandleStream resolves the playable direct URL (e.g. .mp4) for ExoPlayer
func (h *Handler) HandleStream(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "movie id is required"})
		return
	}

	server := r.URL.Query().Get("server")
	if server == "" {
		server = "1"
	}

	streamResp, err := h.resolver.ResolveStream(id, server)
	if err != nil {
		log.Printf("Stream resolution error for ID %s (server %s): %v", id, server, err)
		writeJSON(w, http.StatusBadGateway, map[string]string{
			"error":   "Failed to resolve video stream",
			"details": err.Error(),
		})
		return
	}

	writeJSON(w, http.StatusOK, streamResp)
}

// HandleSearch performs fast search across the catalog (and live scrape fallback)
func (h *Handler) HandleSearch(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if query == "" {
		writeJSON(w, http.StatusOK, []model.Movie{})
		return
	}

	results := h.store.Search(query, 20)

	// If local store has few matches, perform a live search on CineSubz and index results
	if len(results) < 5 {
		liveResults, err := h.scraper.ScrapeSearch(query)
		if err == nil {
			for i := range liveResults {
				h.store.Upsert(&liveResults[i])
			}
			results = h.store.Search(query, 20)
		}
	}

	writeJSON(w, http.StatusOK, results)
}

// HandleRefresh triggers an immediate catalog scrape of the latest pages
func (h *Handler) HandleRefresh(w http.ResponseWriter, r *http.Request) {
	go func() {
		for page := 1; page <= 3; page++ {
			movies, err := h.scraper.ScrapeCatalogPage(page)
			if err != nil {
				log.Printf("Catalog scrape error on page %d: %v", page, err)
				break
			}
			for i := range movies {
				h.store.Upsert(&movies[i])
			}
		}
		log.Printf("Catalog refresh completed. Total indexed: %d", h.store.Count())
	}()

	writeJSON(w, http.StatusAccepted, map[string]string{
		"message": "Catalog refresh started in background",
	})
}
