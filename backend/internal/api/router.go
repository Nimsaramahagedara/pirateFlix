package api

import (
	"log"
	"net/http"
	"os"
	"time"
)

// SetupRouter creates and returns the configured HTTP handler with middleware
func SetupRouter(h *Handler) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/health", h.HandleHealth)
	mux.HandleFunc("GET /api/home", h.HandleHome)
	mux.HandleFunc("GET /api/movies", h.HandleMovies)
	mux.HandleFunc("GET /api/movie/{id}", h.HandleMovieByID)
	mux.HandleFunc("GET /api/series/{id}", h.HandleSeriesByID)
	mux.HandleFunc("GET /api/stream/{id}", h.HandleStream)
	mux.HandleFunc("GET /api/search", h.HandleSearch)
	mux.HandleFunc("POST /api/refresh", h.HandleRefresh)

	// Serve Netflix web viewer at root
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		if _, err := os.Stat("index.html"); err == nil {
			http.ServeFile(w, r, "index.html")
			return
		}
		http.ServeFile(w, r, "../index.html")
	})

	// Wrap with middleware
	return withCORS(withLogging(withRecovery(mux)))
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func withLogging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s in %v", r.Method, r.URL.Path, time.Since(start))
	})
}

func withRecovery(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				log.Printf("PANIC recovered: %v", err)
				http.Error(w, "Internal Server Error", http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}
