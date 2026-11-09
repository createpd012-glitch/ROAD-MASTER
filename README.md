# ROAD MASTER Android

Kotlin + Jetpack Compose + MapLibre starter for an Android road-exploration game.

## Included in this prototype
- Four-tab Compose UI: map, ranking, history, profile.
- MapLibre map using OpenStreetMap raster tiles for development/demo use, centered on South Korea.
- Foreground GPS service with a persistent notification.
- GPS fixes with accuracy <= 50 m are broadcast to the active map screen.
- Current-session GPS polyline appears after two points.
- Visit-count scoring/color model: 10/20/30/40/50 points and five color tiers.
- GitHub Actions workflow to build a debug APK and publish it as an artifact.

## Not production-ready yet
- This source has not yet been verified by a successful Android build.
- GPS points are temporary in-memory UI state and are not persisted or uploaded.
- No road-network map matching, canonical segment IDs, repeat-visit validation, or production scoring.
- No real authentication, Firestore rules, backend functions, or live rankings. Ranking/profile values are illustrative UI content.
- Visited-road colored segment overlays are not connected; only the current session line is blue.

## Build on GitHub
1. Open the repository's **Actions** tab.
2. Select **Build ROAD MASTER APK**.
3. Select **Run workflow**.
4. When the run succeeds, open the run and download the `ROAD-MASTER-debug-APK` artifact.
5. Extract `app-debug.apk` and install it on an Android device.

The workflow runs on GitHub-hosted infrastructure with JDK 17 and Gradle 8.9.

## Local Android Studio setup
Open the repository root in Android Studio. The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and min SDK 26.

## Map tiles
The current style uses `https://tile.openstreetmap.org/{z}/{x}/{y}.png` as a development/demo basemap and displays © OpenStreetMap contributors. This public endpoint is not a guaranteed production tile service. Before public release, choose a provider or host your own tiles, verify Korea coverage and commercial/traffic terms, configure caching and rate limits, and update the style in `RoadMapView.kt`.

## Firebase and backend
Firebase dependencies are included, but authentication and Firestore are not configured. Before enabling them, create a Firebase Android app with package name `com.roadmaster.app`, add `google-services.json`, configure sign-in, deploy trusted backend functions, and add restrictive security rules. Never put service-account credentials or private API secrets in the Android app.

## Privacy and safety
- Keep location collection off until the user explicitly starts a session.
- Show a persistent foreground-service notification and a clear stop action.
- Do not publish live location, exact routes, home/work endpoints, or another user's raw track.
- Minimize raw location retention and provide deletion controls.
- Do not prompt for interaction while moving or reward speed. Check applicable age/guardian consent and privacy requirements before launch.

A successful cloud build is required before an APK exists. This prototype does not yet provide production road matching, account login, persistent scoring, or live rankings.