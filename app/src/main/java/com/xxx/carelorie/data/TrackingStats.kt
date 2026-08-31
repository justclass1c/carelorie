package com.xxx.carelorie.data

import java.time.LocalDate

/**
 * The three numbers on the profile header, plus the date the account started.
 *
 * Computed from the full set of logged days rather than from whatever window a screen happens to
 * have fetched — the dashboard streak used to do the latter and capped at the day of the month.
 */
data class TrackingStats(
    val activeStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalTracked: Int = 0,
    val memberSince: LocalDate? = null
) {
    companion object {
        fun from(loggedDates: Set<LocalDate>, accountCreated: LocalDate?, today: LocalDate): TrackingStats {
            if (loggedDates.isEmpty()) {
                return TrackingStats(memberSince = accountCreated)
            }

            val sorted = loggedDates.sorted()

            // Yesterday anchors the streak when today has nothing logged yet, so the number does
            // not drop to zero every morning before the first meal.
            val anchor = when {
                loggedDates.contains(today) -> today
                loggedDates.contains(today.minusDays(1)) -> today.minusDays(1)
                else -> null
            }
            var active = 0
            var cursor = anchor
            while (cursor != null && loggedDates.contains(cursor)) {
                active++
                cursor = cursor.minusDays(1)
            }

            var longest = 1
            var run = 1
            for (i in 1 until sorted.size) {
                run = if (sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
                if (run > longest) longest = run
            }

            return TrackingStats(
                activeStreak = active,
                longestStreak = maxOf(longest, active),
                totalTracked = loggedDates.size,
                // Fall back to the first day they logged anything: accounts created before the
                // createdAt column existed have no other answer, and this one is truer anyway.
                memberSince = accountCreated ?: sorted.first()
            )
        }
    }
}
