package indexer

import (
	"strings"
	"sync"

	"cinesubz-backend/internal/model"
)

// Store holds the in-memory movie catalog with thread-safe indexing.
type Store struct {
	mu          sync.RWMutex
	moviesByID  map[string]*model.Movie
	moviesByURL map[string]*model.Movie
	allMovies   []*model.Movie
	byGenre     map[string][]*model.Movie
	trending    []*model.Movie
	tvshows     []*model.Movie
	seriesByID  map[string]*model.SeriesDetail
}

// NewStore creates a new in-memory catalog store.
func NewStore() *Store {
	return &Store{
		moviesByID:  make(map[string]*model.Movie),
		moviesByURL: make(map[string]*model.Movie),
		allMovies:   make([]*model.Movie, 0),
		byGenre:     make(map[string][]*model.Movie),
		trending:    make([]*model.Movie, 0),
		tvshows:     make([]*model.Movie, 0),
		seriesByID:  make(map[string]*model.SeriesDetail),
	}
}

// Upsert adds or updates a movie in the store.
func (s *Store) Upsert(m *model.Movie) {
	if m == nil || m.ID == "" {
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	existing, exists := s.moviesByID[m.ID]
	if exists {
		// Update fields
		*existing = *m
		return
	}

	// Add new movie
	s.moviesByID[m.ID] = m
	s.moviesByURL[m.PageURL] = m
	s.allMovies = append([]*model.Movie{m}, s.allMovies...) // prepend newest

	// Index by genres
	for _, g := range m.Genres {
		normalized := strings.ToLower(strings.TrimSpace(g))
		if normalized != "" {
			s.byGenre[normalized] = append(s.byGenre[normalized], m)
		}
	}
}

// UpsertTrending indexes a batch of trending movies.
func (s *Store) UpsertTrending(movies []model.Movie) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trending = make([]*model.Movie, 0, len(movies))
	for i := range movies {
		m := &movies[i]
		s.trending = append(s.trending, m)
		if _, exists := s.moviesByID[m.ID]; !exists {
			s.moviesByID[m.ID] = m
			s.moviesByURL[m.PageURL] = m
			s.allMovies = append(s.allMovies, m)
		}
	}
}

// UpsertTVShows indexes a batch of TV shows & series.
func (s *Store) UpsertTVShows(movies []model.Movie) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tvshows = make([]*model.Movie, 0, len(movies))
	for i := range movies {
		m := &movies[i]
		s.tvshows = append(s.tvshows, m)
		if _, exists := s.moviesByID[m.ID]; !exists {
			s.moviesByID[m.ID] = m
			s.moviesByURL[m.PageURL] = m
			s.allMovies = append(s.allMovies, m)
		}
	}
}

// Count returns the total number of indexed movies.
func (s *Store) Count() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return len(s.allMovies)
}

// GetByID looks up a movie by its post ID.
func (s *Store) GetByID(id string) (*model.Movie, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	m, ok := s.moviesByID[id]
	if !ok {
		return nil, false
	}
	cp := *m
	return &cp, true
}

// GetAll returns a paginated list of movies, optionally filtered by genre.
func (s *Store) GetAll(page, limit int, genre string) ([]model.Movie, int) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var source []*model.Movie
	normalizedGenre := strings.ToLower(strings.TrimSpace(genre))
	if normalizedGenre != "" {
		source = s.byGenre[normalizedGenre]
	} else {
		source = s.allMovies
	}

	total := len(source)
	if total == 0 {
		return []model.Movie{}, 0
	}

	if limit <= 0 {
		limit = 20
	}
	if page <= 0 {
		page = 1
	}

	start := (page - 1) * limit
	if start >= total {
		return []model.Movie{}, total
	}

	end := start + limit
	if end > total {
		end = total
	}

	result := make([]model.Movie, end-start)
	for i, m := range source[start:end] {
		result[i] = *m
	}

	return result, total
}

// Search performs a substring/token search over title, original title, and genres.
func (s *Store) Search(query string, limit int) []model.Movie {
	s.mu.RLock()
	defer s.mu.RUnlock()

	q := strings.ToLower(strings.TrimSpace(query))
	if q == "" {
		return []model.Movie{}
	}

	if limit <= 0 {
		limit = 20
	}

	var results []model.Movie
	for _, m := range s.allMovies {
		if strings.Contains(strings.ToLower(m.Title), q) ||
			strings.Contains(strings.ToLower(m.Description), q) ||
			strings.Contains(strings.ToLower(m.Year), q) {
			results = append(results, *m)
			if len(results) >= limit {
				break
			}
		}
	}

	return results
}

// GetHomeFeed produces a Netflix-style grouped payload with a hero banner and categorized rows.
func (s *Store) GetHomeFeed() model.HomeFeed {
	s.mu.RLock()
	defer s.mu.RUnlock()

	feed := model.HomeFeed{
		Rows: make([]model.CategoryRow, 0),
	}

	if len(s.allMovies) == 0 {
		return feed
	}

	// Hero banner: latest movie with a valid poster
	for _, m := range s.allMovies {
		if m.Poster != "" {
			cp := *m
			feed.HeroBanner = &cp
			break
		}
	}

	// Helper to extract top N movies from a slice
	take := func(slice []*model.Movie, n int) []model.Movie {
		count := min(n, len(slice))
		res := make([]model.Movie, count)
		for i := 0; i < count; i++ {
			res[i] = *slice[i]
		}
		return res
	}

	// Row 1: Trending Now
	if len(s.trending) > 0 {
		feed.Rows = append(feed.Rows, model.CategoryRow{
			Title:    "Trending Now",
			Category: "trending",
			Movies:   take(s.trending, 15),
		})
	}

	// Row 2: Latest Movies
	latestMovies := take(s.allMovies, 15)
	if len(latestMovies) > 0 {
		feed.Rows = append(feed.Rows, model.CategoryRow{
			Title:    "Latest Movies",
			Category: "movies",
			Movies:   latestMovies,
		})
	}

	// Row 3: TV Shows & Series
	if len(s.tvshows) > 0 {
		feed.Rows = append(feed.Rows, model.CategoryRow{
			Title:    "TV Shows & Series",
			Category: "tvshows",
			Movies:   take(s.tvshows, 15),
		})
	}

	// Specific popular genres
	targetGenres := []struct {
		Slug  string
		Title string
	}{
		{"action", "Action & Adventure"},
		{"sinhala", "Sinhala Subtitles"},
		{"thriller", "Suspense & Thrillers"},
		{"romance", "Romance & Drama"},
		{"comedy", "Comedy"},
		{"horror", "Horror & Mystery"},
		{"animation", "Animation & Family"},
	}

	for _, g := range targetGenres {
		movies := s.byGenre[g.Slug]
		if len(movies) > 0 {
			feed.Rows = append(feed.Rows, model.CategoryRow{
				Title:    g.Title,
				Category: g.Slug,
				Movies:   take(movies, 12),
			})
		}
	}

	return feed
}

// GetSeries retrieves cached TV series details by show ID.
func (s *Store) GetSeries(id string) (*model.SeriesDetail, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	ser, ok := s.seriesByID[id]
	return ser, ok
}

// SetSeries caches TV series details by show ID.
func (s *Store) SetSeries(id string, ser *model.SeriesDetail) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.seriesByID[id] = ser
}

