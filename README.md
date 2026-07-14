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
