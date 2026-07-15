# Spoldify

A custom Spotify client for old car Android head units and low-resolution
landscape tablets. Built on [librespot-java](https://github.com/librespot-org/librespot-java)
and [librespot-android](https://github.com/devgianlu/librespot-android) for
native playback, with a lightweight XML Views UI optimized for older hardware.

## Prerequisites

- Android Studio (Hedgehog or newer)
- JDK 11+
- Android SDK with `compileSdk` 36
- A Spotify account (Premium required for streaming)

## Spotify Client ID Setup

The app requires its own Spotify application client ID. You must create your own
in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   and log in with your Spotify account.
2. Click **Create app**.
3. Fill in the app name and description (anything you like).
4. Set the **Redirect URI** to exactly:
   ```
   spoldify://auth/callback
   ```
5. Accept the terms and create the app.
6. Open the app's **Settings** page and copy the **Client ID**.

## Configure secrets.properties

The client ID is loaded at build time from a gitignored properties file so it
is never committed to the repository.

```bash
cp secrets.properties.example secrets.properties
```

Edit `secrets.properties` and paste your client ID:

```properties
SPOTIFY_CLIENT_ID=your_client_id_here
```

The build will fail to inject a valid ID if this file is missing, so make sure
this step is done before building.

## Building

```bash
# Clone with submodules
git clone --recursive git@github.com:iliverez/spoldify.git
cd spoldify

# Set up your client ID
cp secrets.properties.example secrets.properties
# Edit secrets.properties with your Spotify client ID

# Build debug APK
./gradlew assembleDebug
```

The built APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

To install directly to a connected device or emulator:

```bash
./gradlew installDebug
```

## Login Methods

Spoldify supports three login methods, all available from the login screen.

### Username / Password

Enter your Spotify username and password directly. Optionally tick
**Remember me** to store credentials on the device for automatic re-login.
This uses librespot's session API.

### OAuth (Authorization Code with PKCE)

Tap **Sign in with Spotify** to open Spotify's OAuth flow in a browser. After
authorizing, you are redirected back to the app via the
`spoldify://auth/callback` redirect URI configured earlier.

On head units without a browser, there is also an in-app **AuthWeb** screen
that loads the OAuth page in a WebView.

#### QR code token upgrade (recommended after first login)

After logging in via username/password or Spotify Connect, the app uses a
librespot session token for Web API calls (home, search, playlists). This
token is tied to librespot's shared client ID and is subject to aggressive
rate limiting (HTTP 429). Completing the OAuth flow below upgrades the Web
API token to your own client ID, giving the app its own rate-limit budget.

**You only need to do this once.** A refresh token is stored permanently and
used to obtain fresh access tokens on every subsequent app start. The session
login (for playback) is unaffected.

**Prerequisites:**
- The head unit and your phone must be on the same Wi-Fi network.
- Complete this from the **Home** screen (not the login screen).

**Steps:**

1. On the Spoldify home screen, tap **Sign in with Spotify**.
2. A QR code appears on the head unit screen, along with a text field for
   manual code entry.
3. Scan the QR code with your phone (camera app or any QR scanner). This
   opens Spotify's authorization page in your phone's browser.
4. Log in to Spotify if prompted, review the requested permissions, and tap
   **Agree**.
5. Spotify redirects back through the head unit's local server. The app
   exchanges the authorization code for access and refresh tokens.
6. The home screen reloads automatically. The status text changes to
   **Connected** and the QR code disappears.

**Alternative if no QR scanner is available:**
- If the head unit has a browser, the OAuth URL opens automatically.
- If neither works, copy the full callback URL
  (`spoldify://auth/callback?code=...`) from the browser's address bar after
  authorization and paste it into the text field on the head unit, then tap
  **Submit**.

**What happens behind the scenes:**

1. The app starts a local HTTP server (`TokenExchangeServer`) and generates
   an OAuth authorization URL containing your client ID and PKCE challenge.
2. The QR code encodes the local server URL so your phone's browser can reach
   it over Wi-Fi.
3. After you authorize on Spotify, the callback delivers an authorization
   code to the local server.
4. The app exchanges the code at `accounts.spotify.com/api/token` for an
   access token and a refresh token, both tied to your client ID.
5. The `UserTokenManager` stores the refresh token and marks the token as
   OAuth-sourced. On every subsequent start, it refreshes the access token
   using your client ID instead of falling back to the rate-limited
   librespot session token.

### Spotify Connect (Zeroconf)

This is the recommended method for car head units. Instead of entering
credentials, Spoldify advertises itself as a Spotify Connect device on your
local network. You then "hand off" your session from the Spotify phone app.

**How it works:**

1. On the Spoldify login screen, tap **Connect from Phone**.
2. Spoldify starts a Zeroconf (mDNS) server on port `38475`, advertising itself
   as a Spotify Connect device.
3. Open the Spotify app on your phone (connected to the same Wi-Fi network).
4. Tap the **Devices** icon (top-right speaker icon) and select **Spoldify**
   from the list.
5. Spotify transfers the session to Spoldify. The app logs in automatically
   and playback can be controlled from the head unit.

This method does not require entering any credentials on the device.

#### Using Zeroconf with the Android Emulator

The emulator is on its own virtual network, so the Spotify phone app cannot
discover it directly. The `scripts/publish-zeroconf.sh` helper bridges the gap
by publishing the mDNS service on the host machine's real network and
forwarding traffic to the emulator.

**Prerequisites (Fedora):**

```bash
sudo dnf install socat avahi-tools
```

**Usage:**

1. Start the emulator and install the app.
2. Tap **Connect from Phone** in Spoldify.
3. Run the publisher script:

   ```bash
   ./scripts/publish-zeroconf.sh
   ```

4. Open Spotify on your phone and select **Spoldify** from the device list.
5. Press `Ctrl+C` in the terminal when done.

The connection flow is:

```
Phone → <host-ip>:48475 (socat) → 127.0.0.1:38475 (adb forward) → emulator:38475
```

## Architecture

| Layer | Details |
|-------|---------|
| UI | XML Views with ViewBinding, MVVM with `ViewModel` + `LiveData` |
| Navigation | Android Navigation Component with bottom nav |
| Data | Repository pattern wrapping Spotify Web API and librespot sessions |
| Playback | `PlayerService` foreground service wrapping librespot player |
| Auth | Username/password, OAuth PKCE, and Spotify Connect (Zeroconf) |

## License

MIT
