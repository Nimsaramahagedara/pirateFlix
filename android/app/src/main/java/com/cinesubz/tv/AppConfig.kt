package com.cinesubz.tv

object AppConfig {
    /**
     * Set this to your Go backend service URL:
     * - Android Emulator: "http://10.0.2.2:8080/api/"
     * - Physical Android TV: "http://192.168.x.x:8080/api/" (Your machine's local IP)
     * - Production Cloud: "https://your-backend.domain.com/api/"
     */
    const val BASE_URL = "http://10.0.2.2:8080/api/"

    // Origin header required by CineSubz video servers
    const val DEFAULT_REFERER = "https://cinesubz.lk"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android TV)"
}
