# ROAD MASTER — map matching, scoring and backend plan

## 1. Current implementation boundary

Implemented in the Android client:
- MapLibre Native map centered on South Korea.
- OpenStreetMap raster tiles for development/demo viewing, with visible attribution.
- User-started foreground GPS service and persistent notification.
- GPS validation for coordinate range, accuracy, stale fixes and duplicate timestamps.
- Live current-session route line in blue.
- A current-location marker and camera move action.
- Map layers prepared for five server-confirmed road-visit color tiers.
- Kotlin data contracts for sessions, GPS samples, match requests/results, road visits and leaderboard entries.

Not implemented:
- No GPS persistence across process death yet; the current route list is in app memory.
- No upload of GPS samples.
- No map-matching server is connected.
- No canonical road segment IDs are returned to the app.
- The five visit-tier map layers are ready, but they are not populated by a backend yet.
- No Firebase sign-in, production Firestore rules, scoring transactions or live rankings.

## 2. Recommended architecture

Android client
1. Ask for location permission only when the player starts a session or taps My Location.
2. Create a session ID and collect timestamped fixes while the session is active.
3. Keep the current session line local and visibly indicate when recording is active.
4. Upload samples in bounded batches only after authentication and consent are configured.
5. Render only the matched geometry and aggregate visit counts returned by the backend.

Trusted backend
1. Verify Firebase ID token, session owner, consent version and request limits.
2. Validate timestamps, coordinate bounds, accuracy, duplicate batch IDs and implausible jumps.
3. Send a sequence of samples to a map-matching engine.
4. Convert matched road edges into canonical segment IDs for the current road-graph version.
5. Reject low-confidence matches and apply traversal/cooldown rules.
6. In one server transaction, append the accepted visit event and update user and leaderboard aggregates.
7. Return segment geometry and visit count so the Android client can color the road.

The Android client must never submit an authoritative score, visit count or leaderboard rank.

## 3. Road matching engine

Recommended first technical evaluation: self-host OSRM Match or Valhalla Meili against an OpenStreetMap road extract covering South Korea. This avoids placing a paid map-matching key in the APK, but still requires a server, data updates, monitoring and capacity planning.

OSRM's Match service snaps a GPS trace to the most plausible road network path. Its documentation notes that traces can split when there are large timestamp gaps or implausible transitions, and that some points can be treated as outliers. Handle multiple sub-traces and unmatched points; do not force every GPS point onto a road.

Official references:
- OSRM HTTP API / Match service: https://project-osrm.org/docs/v26.6.1/http.html
- OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/

Do not use a public demo matching endpoint as the production backend. If a hosted matching provider is selected instead, confirm Korean road coverage, pricing, request limits, retention terms and whether an API key is required. Any provider key must be supplied by the project owner and stored server-side, not hard-coded in the APK.

## 4. Suggested API contract

### Create session
POST /v1/track-sessions

Request:
- consentVersion
- clientStartedAt
- appVersion

Response:
- sessionId
- serverStartedAt
- uploadBatchLimit

### Upload a batch
POST /v1/track-sessions/{sessionId}/samples:batch

Request:
- batchId (UUID; idempotency key)
- sequenceStart
- samples[]: latitude, longitude, accuracyMeters, speedMetersPerSecond, bearingDegrees, recordedAtMillis

Response:
- acceptedCount
- rejectedCount
- processingState

Use bounded batches (for example, 50–100 samples), retry with the same batchId, and make server writes idempotent. Do not upload every GPS fix as a separate request.

### Process matching
POST /v1/track-sessions/{sessionId}/match

Response:
- roadGraphVersion
- matchedSegments[]: segmentId, confidence, distanceToRoadMeters, matchedDistanceMeters, roadClass, snappedGeometry
- unmatchedSampleCount
- processingState

### Read visited roads for the visible map area
GET /v1/me/road-visits?bbox=minLon,minLat,maxLon,maxLat

Response:
- segmentId
- visitCount
- colorHex
- geometry
- roadGraphVersion

The client maps visitCount 1–5 to the five color tiers in Scoring. Visit 5 and later use the final purple tier.

### Read leaderboards
GET /v1/leaderboards?scope=national|region&period=weekly&regionCode={optional}

Weekly period keys should be generated on the server using Asia/Seoul calendar rules (ISO week, e.g. 2026-W46). Regional rankings should use an explicit user-selected region code or a privacy-reviewed region assignment, not expose precise GPS history.

## 5. Firestore document proposal

- users/{uid}
  - nickname, regionCode, createdAt, totalPoints, uniqueRoadCount, totalDistanceMeters
- trackSessions/{sessionId}
  - uid, startedAt, endedAt, status, consentVersion, appVersion, rawSampleExpiresAt
- trackSessions/{sessionId}/batches/{batchId}
  - receivedAt, sequenceStart, sampleCount, payloadHash, processingStatus
- trackSessions/{sessionId}/matchedSegments/{segmentEventId}
  - segmentId, roadGraphVersion, confidence, matchedDistanceMeters, accepted
- users/{uid}/roadVisits/{segmentId}
  - segmentId, roadGraphVersion, visitCount, totalPoints, firstVisitedAt, lastVisitedAt, lastSessionId, colorTier
- leaderboardPeriods/{periodKey}/entries/{uid}
  - uid, nicknameSnapshot, regionCode, totalPoints, uniqueRoadCount, updatedAt

Use server timestamps for authoritative time. Deny mobile clients write access to score totals, roadVisits, matched segment events and leaderboard entries. Only trusted backend functions/service accounts should update these documents.

## 6. Visit validation and scoring proposal

A GPS sample is not a visit. A visit should be counted only after the matcher confirms a plausible traversal of a canonical segment.

Initial tunable policy:
- Minimum match confidence: 0.70.
- Minimum matched traversal length: 30 m, subject to road-class testing.
- Reject low-accuracy, stale, duplicate and implausible samples.
- Count at most one traversal of the same segment per session and apply a server-side revisit cooldown (initial proposal: 6 hours).
- Keep direction handling explicit; decide whether opposite-direction traversal is the same segment before launch.

Progressive points per accepted visit:
- Visit 1: +10 points, blue #2389FF.
- Visit 2: +20 points, green #22C88A.
- Visit 3: +30 points, yellow #FFC247.
- Visit 4: +40 points, orange #FF873D.
- Visit 5 and later: +50 points, purple #C16BFF.

The server increments visitCount and calculates the awarded points atomically. The client only renders the server-returned color tier. Tune thresholds with real Korean urban, rural, elevated-road and tunnel traces before launch.

## 7. Map tiles and user-supplied configuration

The current demo map uses the standard OpenStreetMap raster tile URL and does not require a private API key. This is for limited development/testing only. OpenStreetMap's tile servers are best-effort, have no SLA, and impose usage requirements including visible attribution, a clear application User-Agent, and no bulk prefetch/offline tile downloads. A public app should use a properly licensed tile provider or self-hosted tiles.

If a production provider requires a key, the project owner must create and supply it. Do not put private service keys in the Android APK. Configure the provider URL and public client token through the approved app configuration mechanism, and keep server secrets on the backend.

Firebase is not configured. To enable accounts and cloud data:
1. Create a Firebase project and Android app with package name com.roadmaster.app.
2. Download the matching google-services.json and place it at app/google-services.json.
3. Enable the chosen sign-in methods.
4. Deploy backend functions and restrictive Firestore rules before allowing real users.
5. Add account deletion and data deletion/export flows.

No Firebase project, API key, or secret has been invented in this repository.

## 8. Privacy, reliability and release checklist

- Explain why location is collected and obtain clear consent before each recording session.
- Keep a visible recording state and an obvious stop action.
- Avoid public live location, raw route sharing and home/work endpoint exposure.
- Minimize raw GPS retention; delete raw samples after matching and the required retry window.
- Provide account and location-data deletion.
- Avoid interaction prompts while moving and never reward speed.
- Verify applicable child/guardian consent and privacy requirements before any public launch.
- Test battery usage, background behavior, permission denial, stale fixes, tunnels, GPS drift and app process recreation.
- Select and contract a production tile provider; verify attribution and Korean coverage.
