package com.xxx.carelorie

import com.xxx.carelorie.data.nutrition.FoodCandidate
import com.xxx.carelorie.data.remote.RemoteFoodPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Guards the quantity handling behind the diary's edit feature.
 *
 * The multiplier used to be folded into the food's name, which left nothing to edit afterwards.
 * These tests exist so that cannot come back.
 */
class FoodCandidateTest {

    private val nasiLemak = RemoteFoodPreset(
        name = "Nasi Lemak",
        calories = 644,
        protein = 17f,
        carbs = 81f,
        fat = 27f
    )

    @Test
    fun `scaling does not touch the name`() {
        val logged = FoodCandidate(nasiLemak, quantity = 2f).toLoggablePreset()
        assertEquals("Nasi Lemak", logged.name)
        assertFalse(logged.name.contains("x2"))
    }

    @Test
    fun `macros scale with quantity`() {
        val logged = FoodCandidate(nasiLemak, quantity = 2f).toLoggablePreset()
        assertEquals(1288, logged.calories)
        assertEquals(34f, logged.protein, 0.01f)
        assertEquals(162f, logged.carbs, 0.01f)
        assertEquals(54f, logged.fat, 0.01f)
    }

    @Test
    fun `half servings scale correctly`() {
        val logged = FoodCandidate(nasiLemak, quantity = 0.5f).toLoggablePreset()
        assertEquals(322, logged.calories)
        assertEquals(8.5f, logged.protein, 0.01f)
    }

    @Test
    fun `a single serving returns the preset untouched`() {
        val candidate = FoodCandidate(nasiLemak, quantity = 1f)
        assertSame(nasiLemak, candidate.toLoggablePreset())
    }

    @Test
    fun `candidate totals match the loggable preset`() {
        // The review screen shows candidate.calories; the diary stores toLoggablePreset().
        // They must agree, or the totals shown before logging differ from the ones stored.
        val candidate = FoodCandidate(nasiLemak, quantity = 3f)
        val logged = candidate.toLoggablePreset()
        assertEquals(candidate.calories, logged.calories)
        assertEquals(candidate.protein, logged.protein, 0.01f)
        assertEquals(candidate.carbs, logged.carbs, 0.01f)
        assertEquals(candidate.fat, logged.fat, 0.01f)
    }

    @Test
    fun `rescaling an edited entry recovers the original totals`() {
        // Mirrors FoodRepository.updateLog: totals are stored for the logged servings, so an
        // edit rescales by newQuantity / oldQuantity. Going 1 -> 3 -> 1 must round-trip.
        val storedAtOne = FoodCandidate(nasiLemak, quantity = 1f).toLoggablePreset()

        val toThree = storedAtOne.calories * (3f / 1f)
        val backToOne = toThree * (1f / 3f)

        assertEquals(storedAtOne.calories.toFloat(), backToOne, 0.01f)
    }

    @Test
    fun `each candidate gets its own selection id`() {
        // Selection is keyed by this, so two of the same food must not collapse into one.
        val a = FoodCandidate(nasiLemak)
        val b = FoodCandidate(nasiLemak)
        assertFalse(a.selectionId == b.selectionId)
    }
}
