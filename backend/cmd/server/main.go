package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"cinesubz-backend/internal/api"
	"cinesubz-backend/internal/indexer"
	"cinesubz-backend/internal/resolver"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Println("Initializing CineSubz Android TV Backend Service...")

	store := indexer.NewStore()
	scraper := indexer.NewScraper()
	streamResolver := resolver.NewStreamResolver()
	handler := api.NewHandler(store, scraper, streamResolver)
	router := api.SetupRouter(handler)

	// Perform initial catalog load in background
	go func() {
		log.Println("Starting initial catalog indexing...")
		initialPages := 3 // First 3 pages ~90 movies
		for p := 1; p <= initialPages; p++ {
			movies, err := scraper.ScrapeCatalogPage(p)
			if err != nil {
				log.Printf("Initial catalog error on page %d: %v", p, err)
				continue
			}
			for i := range movies {
				store.Upsert(&movies[i])
			}
			log.Printf("Indexed page %d (%d movies so far)", p, store.Count())
			time.Sleep(300 * time.Millisecond) // Gentle rate limit
		}

		// Scrape Trending Now
		log.Println("Scraping Trending Now movies...")
		if trending, err := scraper.ScrapeTrendingPage(1); err == nil {
			store.UpsertTrending(trending)
			log.Printf("Indexed %d Trending Now movies", len(trending))
		} else {
			log.Printf("Trending scrape error: %v", err)
		}

		// Scrape TV Shows & Series
		log.Println("Scraping TV Shows & Series...")
		if tvshows, err := scraper.ScrapeTVShowsPage(1); err == nil {
			store.UpsertTVShows(tvshows)
			log.Printf("Indexed %d TV Shows", len(tvshows))
		} else {
			log.Printf("TV Shows scrape error: %v", err)
		}

		log.Printf("Initial catalog indexing finished. Total items: %d", store.Count())
	}()

	// Periodic refresh every 30 minutes
	go func() {
		ticker := time.NewTicker(30 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			log.Println("Running periodic catalog refresh...")
			for p := 1; p <= 2; p++ {
				movies, err := scraper.ScrapeCatalogPage(p)
				if err == nil {
					for i := range movies {
						store.Upsert(&movies[i])
					}
				}
			}
			if trending, err := scraper.ScrapeTrendingPage(1); err == nil {
				store.UpsertTrending(trending)
			}
			if tvshows, err := scraper.ScrapeTVShowsPage(1); err == nil {
				store.UpsertTVShows(tvshows)
			}
		}
	}()

	server := &http.Server{
		Addr:         ":" + port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Server run loop
	go func() {
		log.Printf("Server listening on http://0.0.0.0:%s", port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server listen error: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server gracefully...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced shutdown: %v", err)
	}

	log.Println("Server exited cleanly.")
}
