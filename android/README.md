# CineSubz Android TV - Native Kotlin Client

An ultra-lightweight, high-performance native **Android TV** streaming application built in **Kotlin** designed for budget Android TV devices (1GB–2GB RAM), Chromecast with Google TV, Xiaomi Mi Box, and Fire TV Stick.

---

## Key Highlights

- **Ultra-Lightweight Memory Footprint**: Uses native Android Views, RecyclerView, and Leanback focus selectors rather than heavy WebViews or JavaScript wrappers, keeping memory usage around **~35 MB – 50 MB RAM**.
- **10-Foot Netflix UI**:
  - Top cinematic Hero Banner with backdrop, title, year, IMDb ID, quality, and synopsis.
  - Horizontal carousels (*Latest Releases*, *Sinhala Subtitles*, etc.).
  - Smooth 60 FPS D-pad zoom animation on card focus (`scale 1.08x`).
- **AndroidX Media3 (ExoPlayer)**:
  - Native hardware-accelerated 1080p video decoding.
  - Automated `Referer: https://cinesubz.lk` HTTP header injection, streaming directly from origin CDNs without proxying video through your server.
  - Full TV remote media controls (D-Pad Center/Play/Pause, D-Pad Left 10s Rewind, D-Pad Right 10s Fast-Forward).

---

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml        # TV Leanback declarations, banner & permissions
│   │   ├── java/com/cinesubz/tv/
│   │   │   ├── AppConfig.kt           # Backend IP & URL configuration
│   │   │   ├── model/                 # Movie, HomeFeed, StreamResponse
│   │   │   ├── network/               # Retrofit ApiService & OkHttpClient
│   │   │   ├── adapter/
│   │   │   │   ├── MovieAdapter.kt    # Movie card adapter with D-pad focus scaling
│   │   │   │   └── CategoryAdapter.kt # Horizontal carousel row adapter
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt    # Home browse screen controller
│   │   │       └── PlayerActivity.kt  # Fullscreen ExoPlayer controller
│   │   └── res/
│   │       ├── layout/                # TV XML layouts
│   │       ├── drawable/              # TV banner, card focus borders, gradients
│   │       └── values/                # Colors, strings, and dark TV themes
│   └── build.gradle.kts               # Media3, Retrofit, Coil dependencies
├── build.gradle.kts                   # Top-level Gradle configuration
└── settings.gradle.kts                # Gradle settings
```

---

## Setup & Running

### Step 1: Configure Backend IP
Open `app/src/main/java/com/cinesubz/tv/AppConfig.kt`:
* If running on **Android Studio Emulator**:
  ```kotlin
  const val BASE_URL = "http://10.0.2.2:8080/api/"
  ```
* If running on a **Physical Android TV / Fire TV Stick** connected to your local Wi-Fi:
  ```kotlin
  const val BASE_URL = "http://192.168.1.50:8080/api/" // Replace with your PC's LAN IP
  ```

### Step 2: Open in Android Studio
1. Launch **Android Studio**.
2. Click **Open** and select the `android` folder (`c:\Projects\Private\Movie APP\android`).
3. Allow Gradle to sync dependencies automatically.

### Step 3: Run on Android TV
* **Emulator**: Create an AVD with target **Android TV (1080p)** (API 30–34) and click **Run**.
* **Physical Device (ADB)**:
  1. On your Android TV, go to **Settings → Device Preferences → About → Build** and click 7 times to enable Developer Options.
  2. Enable **Network Debugging / USB Debugging**.
  3. Connect from your PC:
     ```bash
     adb connect <TV_IP_ADDRESS>:5555
     ```
  4. In Android Studio, select your TV device and click **Run**.
