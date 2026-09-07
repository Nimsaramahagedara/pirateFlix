# Build stage
FROM golang:1.24-alpine AS builder

WORKDIR /app

# Copy module definition
COPY go.mod ./

# Copy all source files
COPY . .

# Build statically compiled binary with CGO disabled and stripped debug symbols
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o cinesubz-server ./cmd/server

# Runtime stage
FROM alpine:3.21

# Install CA certificates for HTTPS scraping & timezone support
RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/cinesubz-server /app/cinesubz-server

# Default port
EXPOSE 8080

ENV PORT=8080

# Health check using wget (provided by busybox)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/api/health || exit 1

ENTRYPOINT ["/app/cinesubz-server"]
