package com.xxx.carelorie

import com.xxx.carelorie.data.TrackingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrackingStatsTest {

    private val today = LocalDate.of(2026, 8, 31)

    private fun days(vararg d: String) = d.map { LocalDate.parse(it) }.toSet()

    @Test
    fun `no logs yields zeroes`() {
        val stats = TrackingStats.from(emptySet(), null, today)
        assertEquals(0, stats.activeStreak)
        assertEquals(0, stats.longestStreak)
        assertEquals(0, stats.totalTracked)
        assertNull(stats.memberSince)
    }

    @Test
    fun `streak runs back from today`() {
        val stats = TrackingStats.from(
            days("2026-08-29", "2026-08-30", "2026-08-31"), null, today
        )
        assertEquals(3, stats.activeStreak)
    }

    /** The bug this replaces: a streak that reset every 1st of the month. */
    @Test
    fun `streak crosses a month boundary`() {
        val dates = (0..40).map { today.minusDays(it.toLong()).toString() }
        val stats = TrackingStats.from(days(*dates.toTypedArray()), null, today)
        assertEquals(41, stats.activeStreak)
    }

    @Test
    fun `yesterday anchors the streak before today is logged`() {
        val stats = TrackingStats.from(
            days("2026-08-28", "2026-08-29", "2026-08-30"), null, today
        )
        assertEquals(3, stats.activeStreak)
    }

    @Test
    fun `a two-day gap ends the streak`() {
        val stats = TrackingStats.from(
            days("2026-08-20", "2026-08-21", "2026-08-30", "2026-08-31"), null, today
        )
        assertEquals(2, stats.activeStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(4, stats.totalTracked)
    }

    @Test
    fun `longest streak can predate the active one`() {
        val stats = TrackingStats.from(
            days("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-08-31"),
            null, today
        )
        assertEquals(1, stats.activeStreak)
        assertEquals(4, stats.longestStreak)
    }

    @Test
    fun `member since falls back to the first logged day`() {
        val stats = TrackingStats.from(days("2026-03-14", "2026-08-31"), null, today)
        assertEquals(LocalDate.of(2026, 3, 14), stats.memberSince)
    }

    @Test
    fun `an explicit account date wins over the first log`() {
        val created = LocalDate.of(2026, 1, 5)
        val stats = TrackingStats.from(days("2026-03-14"), created, today)
        assertEquals(created, stats.memberSince)
    }
}
