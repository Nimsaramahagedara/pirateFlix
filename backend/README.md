# CineSubz Android TV Backend Service (Go)

A high-performance, ultra-lightweight backend service written in **Go (Golang)** designed specifically to power a **Netflix-style Android TV application** using CineSubz.

---

## Features

- **Ultra-Lightweight**: Only **~25 MB RAM** working set; single standalone binary with zero external dependencies (pure Go standard library).
- **Automated Catalog Indexing**: Extracts movie listings, posters, clean titles, years, IMDb IDs, and Post IDs without overloading origin servers.
- **In-Memory Thread-Safe Cache**: Instant sub-millisecond response times for Android TV carousels.
- **Stream Resolver**: Resolves direct `.mp4` video streaming links via the CineSubz `/wp-json/zetaplayer/v2` API.
- **ExoPlayer Ready**: Automatically attaches required playback headers (`Referer: https://cinesubz.lk`) so Android TV can play streams directly without proxying heavy video bandwidth through your server.
- **Netflix Home Feed**: Pre-groups catalog items into hero banners and horizontal carousels (`Latest Releases`, `Sinhala Subtitles`, `Action`, etc.).

---

## API Endpoints

### 1. `GET /api/health`
Health check and indexer metrics.
```json
{
  "indexed_movies": 90,
  "status": "ok",
  "uptime_seconds": 120
}
```

### 2. `GET /api/home`
Returns a Netflix-style payload with a Hero Banner and Category Rows ready for D-pad navigation.
```json
{
  "hero_banner": {
    "id": "175219",
    "title": "DC (2026)",
    "poster": "https://cinesubz.lk/wp-content/uploads/2026/09/cttsSTicKEh4Cw0c46gZ1ztHwTW.webp",
    "description": "DC (2026) Sinhala Subtitles | සිංහල උපසිරැසි සමඟ",
    "year": "2026"
  },
  "rows": [
    {
      "title": "Latest Releases",
      "category": "latest",
      "movies": [ ... ]
    },
    {
      "title": "Sinhala Subtitles",
      "category": "sinhala",
      "movies": [ ... ]
    }
  ]
}
```

### 3. `GET /api/stream/:id?server=1`
Resolves direct video stream URL and player headers for a movie.
```json
{
  "post_id": "175219",
  "server": "1",
  "stream_url": "https://player7.csplayer2.space/DC%20(2026)%20Tamil%20WEB-[CineSubz.co]-720p.mp4",
  "type": "mp4",
  "headers": {
    "Referer": "https://cinesubz.lk",
    "User-Agent": "Mozilla/5.0 (Linux; Android TV)"
  }
}
```

### 4. `GET /api/search?q=:query`
Fast in-memory search across titles and descriptions with fallback to live search.
```http
GET /api/search?q=Mongoose
```

### 5. `GET /api/movies?page=1&limit=20&genre=:genre`
Paginated movie listings with optional genre filtering.

### 6. `GET /api/movie/:id`
Full metadata for a single movie.

### 7. `POST /api/refresh`
Triggers immediate background catalog refresh.

---

## How to Build and Run

### Run directly:
```bash
cd backend
go run ./cmd/server
```

### Build standalone binary:
```bash
cd backend
go build -o server.exe ./cmd/server
./server.exe
```

### Configure Port:
```bash
PORT=9000 ./server.exe
```

---

## Android TV (ExoPlayer / Media3) Playback Snippet

In your Android TV app, fetch the stream from `/api/stream/{id}` and pass the returned `headers` to ExoPlayer:

```kotlin
// 1. Fetch stream from backend
val streamResponse = apiService.getStream(movieId)

// 2. Configure ExoPlayer with required Referer header
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent(streamResponse.headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android TV)")
    .setDefaultRequestProperties(streamResponse.headers)

val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
    .createMediaSource(MediaItem.fromUri(streamResponse.streamUrl))

// 3. Play
exoPlayer.setMediaSource(mediaSource)
exoPlayer.prepare()
exoPlayer.play()
```
