# ROAD MASTER Android — v0.2.0

Android-only GPS road exploration game prototype built with Kotlin, Jetpack Compose and MapLibre Native.

## Implemented in the current GitHub main branch

- Full-screen Korea-centered MapLibre map with game HUD controls overlaid on the map.
- OpenStreetMap raster basemap for development/demo viewing, with visible attribution.
- My Location control that performs a one-shot location lookup, moves the camera and displays a custom marker without starting route recording.
- Foreground GPS tracking service with a persistent notification.
- GPS sample validation for coordinate bounds, accuracy, stale fixes and duplicate timestamps.
- Current-session route polyline drawn in blue after at least two accepted GPS fixes.
- Five map overlay layers prepared for server-confirmed road visits: blue, green, yellow, orange and purple.
- Bottom navigation row with icons above labels; Settings includes nickname and background-music controls.
- Kotlin data models for track sessions, GPS batches, map-matching requests/results, road visits and leaderboard entries.
- Progressive score/color policy: +10, +20, +30, +40, then +50 points; visit five and later use the purple tier.
- Unit tests for the scoring/color policy.
- GitHub Actions workflow for unit tests, debug APK build and artifact upload.

## Not yet implemented — do not treat as production features

- GPS route points are currently held in app memory. They are not yet persisted across process death or uploaded to a server.
- The app does not yet call a map-matching engine. Canonical road segment IDs and matched geometry are not yet produced.
- The five visited-road map layers are prepared, but are not populated until a trusted backend returns confirmed visits and geometry.
- The score model is a client-side policy definition only. No points are awarded in the live app.
- Firebase sign-in, account recovery, Firestore security rules, backend functions and real national/regional/weekly rankings are not configured. Ranking rows are illustrative UI data.
- No successful Android build is claimed until the GitHub Actions workflow completes successfully.

## Build a debug APK using GitHub Actions (phone-friendly)

1. Open the repository's **Actions** tab.
2. Select **Build ROAD MASTER APK**.
3. Tap **Run workflow**, or wait for the workflow triggered by a push to `main`.
4. Wait until the run shows a green check.
5. Open the successful run and download the `ROAD-MASTER-debug-APK` artifact.
6. Extract the ZIP and install `app-debug.apk` on the Android device.

The workflow uses JDK 17 and Gradle 8.9 on a GitHub-hosted runner. The APK does not exist until a workflow run succeeds.

## Local Android build

- Android Studio with JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Android SDK 35
- Minimum Android API 26

Open the repository root in Android Studio, sync Gradle, then run the app on a physical Android device. The project currently has no checked-in Gradle wrapper; the GitHub Actions workflow installs Gradle 8.9 directly.

## Map tiles and production licensing

The demo style in `app/src/main/java/com/roadmaster/app/ui/RoadMapView.kt` uses:

`https://tile.openstreetmap.org/{z}/{x}/{y}.png`

No private API key is needed for this limited demo. The standard OpenStreetMap tile service is best-effort, has no SLA, and has usage requirements. It must not be treated as a production tile service for a public app without reviewing and complying with the current tile policy. Use a licensed provider or self-hosted tiles for production; verify Korean coverage, attribution, caching and expected traffic. If a provider requires a key, the project owner must supply it. Do not hard-code private keys in the APK.

## Firebase setup required from the project owner

Firebase is not configured. To enable accounts and cloud data:

1. Create a Firebase project and register an Android app with package name `com.roadmaster.app`.
2. Download the matching `google-services.json` and place it at `app/google-services.json`.
3. Enable the sign-in methods the service will support.
4. Deploy backend functions and restrictive Firestore rules before allowing real users.

No Firebase project, API key or secret has been invented or committed.

## Map matching and scoring backend

See [docs/ROAD_MATCHING_BACKEND.md](docs/ROAD_MATCHING_BACKEND.md) for:
- Recommended OSRM/Valhalla self-hosted matching architecture using a South Korea road graph.
- Request/response contracts for GPS batches and matched road segments.
- Firestore collections for sessions, visits and leaderboards.
- Server-authoritative duplicate/cooldown validation and progressive scoring.
- Nationwide, region and weekly ranking design.

The backend must be authoritative. The Android app must never decide its own score from raw GPS samples.

## Privacy and safety

- The My Location button performs a one-shot fix; continuous route recording starts only after the player explicitly taps 탐험 시작.
- A persistent notification indicates active recording and the app provides a stop control.
- Do not publish live location, exact raw tracks, home/work endpoints or another user's route.
- Minimize raw GPS retention and provide account/location-data deletion before launch.
- Do not prompt for interaction while moving or reward speed.
- Verify applicable child/guardian consent and privacy requirements before public release.
