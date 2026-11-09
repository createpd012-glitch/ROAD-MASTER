package com.roadmaster.app.data

data class RoadVisit(
    val roadSegmentId: String,
    val visitCount: Int,
    val pointsAwarded: Int,
    val totalSegmentPoints: Int,
    val colorHex: String,
    val lastVisitedAtMillis: Long
)
data class PlayerStats(
    val nickname: String = "PLAYER",
    val totalPoints: Int = 0,
    val distanceKm: Double = 0.0,
    val uniqueRoads: Int = 0,
    val repeatVisits: Int = 0
)
data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val recordedAtMillis: Long
)
data class MatchedRoadSegment(
    val segmentId: String,
    val confidence: Double,
    val snappedLatitude: Double,
    val snappedLongitude: Double,
    val distanceToRoadMeters: Double
)
object Scoring {
    val colors = listOf("#2389FF", "#22C88A", "#FFC247", "#FF873D", "#C16BFF")
    fun pointsForVisit(visitCount: Int): Int = visitCount.coerceAtLeast(1).coerceAtMost(5) * 10
    fun colorForVisit(visitCount: Int): String = colors[(visitCount.coerceAtLeast(1) - 1).coerceAtMost(colors.lastIndex)]
}