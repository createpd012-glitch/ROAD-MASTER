package com.roadmaster.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test
    fun pointsIncreaseForEachAcceptedVisitUntilCap() {
        assertEquals(10, Scoring.pointsForVisit(1))
        assertEquals(20, Scoring.pointsForVisit(2))
        assertEquals(30, Scoring.pointsForVisit(3))
        assertEquals(40, Scoring.pointsForVisit(4))
        assertEquals(50, Scoring.pointsForVisit(5))
        assertEquals(50, Scoring.pointsForVisit(6))
    }

    @Test
    fun visitColorChangesByTierAndCapsAtPurple() {
        assertEquals("#2389FF", Scoring.colorForVisit(1))
        assertEquals("#22C88A", Scoring.colorForVisit(2))
        assertEquals("#FFC247", Scoring.colorForVisit(3))
        assertEquals("#FF873D", Scoring.colorForVisit(4))
        assertEquals("#C16BFF", Scoring.colorForVisit(5))
        assertEquals("#C16BFF", Scoring.colorForVisit(99))
    }
}
