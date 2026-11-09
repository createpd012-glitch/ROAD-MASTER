# ROAD MASTER: road matching and scoring backend design

## Current client behavior
- MapLibre renders an OpenStreetMap raster basemap for development/demo.
- The foreground location service broadcasts temporary GPS samples to the open map screen.
- The map draws the current session as a blue line after at least two accepted points.
- The client filters fixes with accuracy worse than 50 m for this preview only.
- The client does not upload tracks, identify road segments, or award points.

## Recommended server-authoritative flow
1. Create a track session only after explicit user consent.
2. Batch GPS samples with accuracy and timestamps; do not upload indefinitely or retain raw coordinates longer than necessary.
3. A trusted backend validates authentication, session ownership, timestamps, accuracy, and duplicate batches.
4. A road-matching service snaps sample sequences to a canonical road graph. Evaluate OSRM, Valhalla, GraphHopper, or a licensed commercial API only after checking Korean coverage, cost, terms, and privacy.
5. Return canonical segment IDs and confidence. Reject low-confidence matches and avoid counting GPS jitter as repeat visits.
6. A server transaction applies visit cooldown rules, awards points, and updates aggregate leaderboards atomically.
7. The app downloads only aggregated road-visit data needed to color map segments. Never expose live coordinates or exact route history publicly.

## Suggested Firestore model
- users/{uid}: nickname, regionCode, createdAt, totalPoints, uniqueRoadCount, totalDistanceKm
- trackSessions/{sessionId}: uid, startedAt, endedAt, status, consentVersion, rawSampleExpiresAt
- trackSessions/{sessionId}/batches/{batchId}: receivedAt, sampleCount, payloadHash, processingStatus
- users/{uid}/roadVisits/{segmentId}: segmentId, visitCount, totalPoints, lastVisitedAt, lastSessionId
- roadSegments/{segmentId}: provider, providerVersion, geometryRef, roadClass, regionCode
- leaderboards/{periodId}/entries/{uid}: uid, nicknameSnapshot, regionCode, points, uniqueRoadCount, updatedAt

Restrict raw samples to server identities, apply short TTL retention, and delete after successful processing or the documented retry window.

## Initial visit / score proposal
- Visit 1: 10 points, blue #2389FF
- Visit 2: 20 points, green #22C88A
- Visit 3: 30 points, yellow #FFC247
- Visit 4: 40 points, orange #FF873D
- Visit 5 and later: 50 points, purple #C16BFF

A visit is a confidently matched traversal, not each GPS sample. Production policy must define traversal distance, direction handling, and cooldown. Never trust a client-submitted visit count or score.

## Production checklist
- Configure Firebase sign-in, Firestore rules, and trusted backend functions.
- Deny client writes to scores, visits, and leaderboard aggregates.
- Select a production map-tile provider whose license and capacity cover expected use.
- Add privacy notice, clear location consent, visible stop control, account deletion, and data export/deletion.
- Avoid public live tracking and route replay by default. Never prompt interaction while moving or reward speed.
