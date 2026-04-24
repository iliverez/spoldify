# Spoldfify - Implementation Plan

## Project Overview

Build a Spotify client for Android 7+ (API 24) optimized for low-resolution landscape tablets, using **librespot-java** (Maven) + **librespot-android** (git submodule for native sink/decoder). UI will use **XML Views** (lighter on old devices than Compose). Architecture: **MVVM** with Repository pattern.

Target resolutions: 640x400, 640x480, 720x400, 720x480, 800x600 (landscape-first).

---

## Phase 1: Project Restructuring & Core Setup

### 1.1 Switch from Compose to XML Views
- Remove Compose plugins and dependencies from `build.gradle.kts` and `libs.versions.toml`
- Remove `buildFeatures { compose = true }`, add `buildFeatures { viewBinding = true }`
- Remove existing Compose theme files (`Theme.kt`, `Color.kt`, `Type.kt`)
- Add XML theme in `themes.xml` (dark Spotify-like theme)

### 1.2 Add librespot-android as git submodule
```bash
git submodule add https://github.com/devgianlu/librespot-android.git librespot-android
```
- Include 3 modules in `settings.gradle.kts`: `:librespot-android-decoder`, `:librespot-android-decoder-tremolo`, `:librespot-android-sink`

### 1.3 Configure dependencies (add to `libs.versions.toml` and `app/build.gradle.kts`):

| Dependency | Purpose |
|---|---|
| `xyz.gianlu.librespot:librespot-lib:1.6.5` | Auth, session, search, metadata, CDN |
| `xyz.gianlu.librespot:librespot-player:1.6.5:thin` | Player (excludes default Java sink) |
| `uk.uuid.slf4j:slf4j-android:1.7.30-0` | SLF4J binding for Android (replaces Log4j) |
| `androidx.lifecycle:viewmodel-ktx` | ViewModel + LiveData |
| `androidx.navigation:navigation-fragment-ktx` | Fragment-based navigation |
| `androidx.navigation:navigation-ui-ktx` | Navigation UI helpers |
| `androidx.media:media` | MediaSessionCompat for notification controls |
| `androidx.preference:preference-ktx` | Settings screen |
| `com.github.bumptech.glide:glide` | Image loading (album art) |
| `androidx.recyclerview:recyclerview` | Efficient lists |
| `androidx.constraintlayout:constraintlayout` | Layout system |
| `com.google.android.material:material` | Material Design components |
| `androidx.core:core-ktx` | (already present) |

### 1.4 Exclude conflicting transitive deps from librespot-player:thin:
```groovy
exclude group: 'xyz.gianlu.librespot', module: 'librespot-sink'
exclude group: 'com.lmax', module: 'disruptor'
exclude group: 'org.apache.logging.log4j'
```

### 1.5 Base architecture scaffolding:
```
com.iliverez.spoldfify/
├── SpoldfifyApp.kt                    (Application class)
├── MainActivity.kt                    (single Activity, hosts NavHostFragment)
├── data/
│   ├── repository/
│   │   ├── AuthRepository.kt          (login, session, credential storage)
│   │   ├── PlayerRepository.kt        (playback state, queue)
│   │   ├── SearchRepository.kt        (search queries)
│   │   ├── LibraryRepository.kt       (saved content, playlists)
│   │   └── HomeRepository.kt          (recent plays, recommendations)
│   ├── local/
│   │   ├── CredentialStorage.kt       (EncryptedSharedPreferences)
│   │   ├── DownloadManager.kt         (offline track storage)
│   │   └── AppPreferences.kt          (SharedPreferences wrapper)
│   └── model/                         (UI models: Track, Album, Artist, Playlist)
├── service/
│   ├── PlayerService.kt               (Foreground service for background playback)
│   ├── PlayerNotificationManager.kt   (Media-style notification)
│   └── MediaSessionCallback.kt        (MediaSessionCompat.Callback)
├── player/
│   └── SpotifyPlayerWrapper.kt        (Wraps librespot Player + session)
├── ui/
│   ├── login/
│   │   ├── LoginFragment.kt
│   │   ├── LoginViewModel.kt
│   │   └── fragment_login.xml
│   ├── home/
│   │   ├── HomeFragment.kt
│   │   ├── HomeViewModel.kt
│   │   └── fragment_home.xml
│   ├── search/
│   │   ├── SearchFragment.kt
│   │   ├── SearchViewModel.kt
│   │   ├── SearchResultsAdapter.kt
│   │   └── fragment_search.xml
│   ├── library/
│   │   ├── LibraryFragment.kt
│   │   ├── LibraryViewModel.kt
│   │   └── fragment_library.xml
│   ├── nowplaying/
│   │   ├── NowPlayingFragment.kt
│   │   ├── NowPlayingViewModel.kt
│   │   └── fragment_now_playing.xml
│   ├── album/
│   │   ├── AlbumDetailFragment.kt
│   │   ├── AlbumDetailViewModel.kt
│   │   └── fragment_album_detail.xml
│   ├── artist/
│   │   ├── ArtistDetailFragment.kt
│   │   ├── ArtistDetailViewModel.kt
│   │   └── fragment_artist_detail.xml
│   ├── playlist/
│   │   ├── PlaylistDetailFragment.kt
│   │   ├── PlaylistDetailViewModel.kt
│   │   └── fragment_playlist_detail.xml
│   ├── settings/
│   │   ├── SettingsFragment.kt        (PreferenceFragmentCompat)
│   │   └── settings.xml               (XML preferences)
│   └── common/
│       ├── adapters/                  (RecyclerView adapters)
│       ├── views/                     (custom views: MiniPlayerBar, etc.)
│       └── widgets/                   (shared UI components)
└── util/
    ├── ImageLoader.kt                 (Glide wrapper, loads art via librespot CDN)
    ├── ConnectionMonitor.kt           (network state tracking)
    └── StorageManager.kt              (storage limit enforcement)
```

### 1.6 AndroidManifest additions:
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` permissions
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions
- `WAKE_LOCK` permission
- `PlayerService` as foreground service with mediaPlayback type
- `android:usesCleartextTraffic="true"` (Spotify CDN may use HTTP)

---

## Phase 2: Authentication

### 2.1 Login screen (`fragment_login.xml`)
- Landscape-optimized: logo left, form right (side-by-side on wide screens)
- Username + password fields, login button
- "Remember me" checkbox (stored in EncryptedSharedPreferences)
- Loading/error states
- On success: create librespot `Session`, store credentials, navigate to Home

### 2.2 Session management (`AuthRepository`)
- `Session.Builder(conf).userPass(user, pass).create()`
- Store credentials via `Session.Builder(conf).stored()` for auto-login
- Maintain singleton session across app lifecycle
- Pass session to `PlayerRepository` and `SpotifyPlayerWrapper`

---

## Phase 3: Playback Engine

### 3.1 SpotifyPlayerWrapper
- Initialize librespot `Player` with the thin variant + Android sink/decoder
- Wrap play/pause/skip/prev/seek/queue operations
- Expose current track state as LiveData
- Handle volume normalization (librespot natively supports this)
- Configure preferred bitrate (96/160/320 kbps) from settings

### 3.2 PlayerService (Foreground Service)
- Start as foreground with `MEDIA_PLAYBACK` type
- Hold a `MediaSessionCompat` for system integration
- Post a media-style notification with: album art, track name, artist, play/pause/skip/prev
- Handle audio focus (duck/pause on incoming calls, etc.)
- Handle `ACTION_PLAY`, `ACTION_PAUSE`, `ACTION_SKIP_TO_NEXT`, `ACTION_SKIP_TO_PREVIOUS` intents

### 3.3 Mini Player Bar (common view at bottom of all screens)
- Visible on all screens except Now Playing
- Shows: album art thumbnail, track name, artist, play/pause button
- Compact height (~48dp) for low-res screens
- Click to expand to Now Playing screen

### 3.4 Now Playing screen (`fragment_now_playing.xml`)
- Landscape layout: album art on left, controls on right
- Track title, artist, album
- Seek bar with time display
- Play/pause, skip, prev, shuffle, repeat buttons
- Compact spacing for low-res

---

## Phase 4: Home Screen

### 4.1 Layout (`fragment_home.xml`) - landscape optimized
- Horizontal scrolling rows (like official Spotify):
  - "Recently Played" (track/album cards)
  - "Made for You" (playlist/album suggestions)
  - "Popular New Releases"
- Each row: title + horizontal RecyclerView
- Each card: album art thumbnail (small), title, subtitle (artist/album)
- Compact card sizes for low-res (e.g., 80x80dp art, 2 lines of text)

### 4.2 Data (`HomeRepository`)
- Use `session.search()` and Mercury API to fetch home content
- Cache responses locally
- Load album art via `session.cdn()` through Glide

---

## Phase 5: Search

### 5.1 Layout (`fragment_search.xml`)
- Search bar at top
- Filter chips: All, Tracks, Albums, Artists, Playlists
- Results as a compact vertical list (RecyclerView)
- Each item: album art thumbnail (48x48dp), title, subtitle, type badge
- Very compact row height (~56dp) to fit many results on low-res

### 5.2 Functionality (`SearchViewModel`)
- Debounced search (300ms) as user types
- Use `session.search().search(query)` with type filters
- Map librespot search results to UI models
- Tap item: navigate to detail (Album/Artist/Playlist) or play (Track)

---

## Phase 6: Detail Screens

- **Album Detail**: album art, title, artist, release date, track list (with numbers, duration, play button)
- **Artist Detail**: artist image, top tracks, albums
- **Playlist Detail**: playlist cover, title, owner, track list
- **Library**: tabs or lists for Playlists, Albums, Artists (from user's library)

All layouts landscape-optimized with compact spacing.

---

## Phase 7: Settings

### 7.1 Using `PreferenceFragmentCompat` with `res/xml/settings.xml`:

| Setting | Type | Options |
|---|---|---|
| Streaming quality | ListPreference | Low (96kbps), Normal (160kbps), High (320kbps) |
| Download quality | ListPreference | Same as above |
| Download via WiFi only | SwitchPreference | On/Off |
| Storage location | Preference (folder picker) | Internal/SD card path |
| Max storage size | SeekBarPreference | 1-50 GB |
| Offline mode | SwitchPreference | On/Off |
| Normalize volume | SwitchPreference | On/Off |
| About | Preference | App version, licenses |

### 7.2 Storage management (`StorageManager`)
- Monitor download directory size against configured limit
- LRU eviction when limit exceeded
- Display current usage in settings

---

## Phase 8: Offline Mode

### 8.1 Download Manager (`DownloadManager`)
- Download tracks to configured storage location
- Use librespot CDN to fetch audio files
- Track download state (pending/downloading/complete/error)
- Show download indicators on tracks/albums

### 8.2 Offline playback
- When offline mode is on or no network: play from local cache
- Show "Offline" indicator in UI
- Queue management for offline tracks

---

## Low-Resolution / Landscape Design Guidelines

These constraints will be applied throughout all phases:

1. **Layout**: All layouts use `layout-land` qualifiers; landscape is the primary design
2. **Compact dimensions**:
   - Standard padding: 8dp (not 16dp)
   - Mini player height: 48dp
   - List item height: 48-56dp
   - Album art thumbnails: 48x48dp (lists), 80x80dp (cards)
   - Text: body 12sp, caption 10sp, title 14sp
3. **Alternative layouts**: Provide `res/layout-sw320dp-land/` for the lowest resolutions
4. **No heavy animations**: Keep transitions simple
5. **Image loading**: Downsample album art aggressively (target ~200px for large displays, 80px for thumbnails)
6. **Color scheme**: Dark theme by default (saves battery on many low-end tablets, Spotify-like aesthetic)

---

## Implementation Order

1. **Phase 1** (project setup) — foundation for everything
2. **Phase 2** (auth) — needed to access any Spotify data
3. **Phase 3.1-3.2** (player wrapper + service) — core playback engine
4. **Phase 3.3-3.4** (mini player + now playing) — visible playback UI
5. **Phase 4** (home) — main screen after login
6. **Phase 5** (search) — core discoverability
7. **Phase 7** (settings) — configure quality, storage, normalization
8. **Phase 6** (detail screens + library) — navigation depth
9. **Phase 8** (offline) — final feature
