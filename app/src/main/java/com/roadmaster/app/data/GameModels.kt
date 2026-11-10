package com.roadmaster.app.data

/**
 * Client/server contract models for ROAD MASTER.
 * Scores and visit counts are always confirmed by the backend, never by raw GPS samples.
 */
enum class TrackSessionStatus {
    RECORDING,
    UPLOAD_PENDING,
    MATCHING,
    COMPLETED,
    FAILED
}

data class TrackSession(
    val sessionId: String,
    val userId: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val status: TrackSessionStatus = TrackSessionStatus.RECORDING,
    val consentVersion: String
)

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val recordedAtMillis: Long,
    val sequenceNumber: Int = 0,
    val bearingDegrees: Float? = null,
    val elapsedRealtimeNanos: Long? = null,
    val provider: String? = "fused"
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/** One canonical edge or sub-edge in a versioned road graph. */
data class MatchedRoadSegment(
    val segmentId: String,
    val roadGraphVersion: String,
    val confidence: Double,
    val snappedGeometry: List<GeoPoint>,
    val distanceToRoadMeters: Double,
    val matchedDistanceMeters: Double,
    val roadClass: String? = null
)

data class MatchTraceRequest(
    val sessionId: String,
    val batchId: String,
    val profile: String,
    val samples: List<GpsSample>
)

data class MatchTraceResponse(
    val sessionId: String,
    val roadGraphVersion: String,
    val matchedSegments: List<MatchedRoadSegment>,
    val unmatchedSampleCount: Int,
    val processedAtMillis: Long
)

/** Aggregate visit state returned by the trusted backend for map coloring. */
data class RoadVisit(
    val roadSegmentId: String,
    val visitCount: Int,
    val pointsAwarded: Int,
    val totalSegmentPoints: Int,
    val colorHex: String,
    val lastVisitedAtMillis: Long,
    val firstVisitedAtMillis: Long = lastVisitedAtMillis,
    val lastSessionId: String? = null,
    val roadGraphVersion: String? = null
)

data class PlayerStats(
    val nickname: String = "PLAYER",
    val totalPoints: Int = 0,
    val distanceKm: Double = 0.0,
    val uniqueRoads: Int = 0,
    val repeatVisits: Int = 0
)

data class LeaderboardEntry(
    val userId: String,
    val nickname: String,
    val regionCode: String?,
    val totalPoints: Int,
    val uniqueRoadCount: Int,
    /** Examples: all-time, 2026-W46, or a region-specific weekly key. */
    val periodKey: String,
    val rank: Int? = null
)

object Scoring {
    val colors = listOf("#2389FF", "#22C88A", "#FFC247", "#FF873D", "#C16BFF")

    /** Progressive visit value: 10, 20, 30, 40, then 50 points per accepted visit. */
    fun pointsForVisit(visitCount: Int): Int =
        visitCount.coerceAtLeast(1).coerceAtMost(colors.size) * 10

    /** Visit five and later keep the final purple tier. */
    fun colorForVisit(visitCount: Int): String =
        colors[(visitCount.coerceAtLeast(1) - 1).coerceAtMost(colors.lastIndex)]
}
