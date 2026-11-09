# Flyre 🎵

> Floating, resizable, and translucent synchronized lyrics overlay for YouTube Music on Android.

## Overview

**Flyre** allows music lovers to view real-time lyrics while listening to YouTube Music without having to switch apps or open a browser. The lyrics float gracefully on top of your active screen with customizable opacity, sizing, and styling.

---

## Key Features

- **Media Detection**: Detects currently playing tracks from YouTube Music via Android's `MediaSessionManager` / `NotificationListenerService`.
- **Floating Window Overlay**: Draggable, resizable floating overlay powered by Android `WindowManager` with system alert permissions.
- **Adjustable Opacity & Styling**: Control window transparency, font size, typeface, and color themes on the fly.
- **Quick Controls**: Minimize into a compact floating bubble or expand to full lyrics view.
- **Lyrics Providers**: Multi-source lyrics fetching (cached local database + fallback online provider APIs).
- **Graceful Fallbacks & Offline Support**: Notifications and manual search options when lyrics are missing.

---

## Technical Architecture

- **Platform**: Android (Targeting Android 5.0+ / API 21+)
- **Language**: Kotlin
- **UI & Layout**: Custom Floating View overlay via `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **Session Tracking**: `NotificationListenerService` / `MediaSessionManager`
- **Network / API**: Retrofit / OkHttp + Coroutines
- **Local Cache**: Room Database / SQLite

---

## Getting Started

### Prerequisites

- Android SDK (API 21+)
- JDK 17+
- Gradle 8.x

### Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

---

## Permissions Required

- `SYSTEM_ALERT_WINDOW`: For drawing the floating lyrics overlay above other applications.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: For detecting currently playing track metadata from YouTube Music.
- `INTERNET`: For querying lyrics APIs.
- `FOREGROUND_SERVICE`: For maintaining reliable background playback tracking.

---

## License

Distributed under the [MIT License](LICENSE).

