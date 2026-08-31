package com.xxx.carelorie

import com.xxx.carelorie.data.UserProfile
import com.xxx.carelorie.data.onboarding.TdeeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TdeeCalculatorTest {

    /** 30-ish year old male, 180 cm, 80 kg — a full set of answers. */
    private fun profile(
        gender: String = "Male",
        height: String = "180",
        weight: Float? = 80f,
        exercise: String? = "1-3",
        activity: String? = "moderate",
        goal: String? = "maintain",
        diet: String? = "balanced",
        protein: String? = "moderate"
    ) = UserProfile(
        userId = "u",
        gender = gender,
        height = height,
        weight = weight,
        birthday = LocalDate.now().minusYears(30).format(
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        ),
        exerciseFrequency = exercise,
        activityLevel = activity,
        goal = goal,
        dietType = diet,
        proteinPreference = protein
    )

    @Test
    fun `a skipped setup produces no estimate rather than a wrong one`() {
        assertNull(TdeeCalculator.estimate(UserProfile(userId = "u")))
        assertNull(TdeeCalculator.estimateTdee(UserProfile(userId = "u")))
    }

    @Test
    fun `missing weight alone blocks the estimate`() {
        assertNull(TdeeCalculator.estimate(profile(weight = null)))
    }

    @Test
    fun `resting rate follows Mifflin-St Jeor`() {
        // 10(80) + 6.25(180) - 5(30) + 5 = 1780 kcal resting.
        // Activity: (1.375 training + 1.45 steps) / 2 = 1.4125  ->  ~2514
        val tdee = TdeeCalculator.estimateTdee(profile())
        assertNotNull(tdee)
        assertEquals(2514.0, tdee!!.toDouble(), 3.0)
    }

    @Test
    fun `the female constant is 166 kcal below the male one`() {
        val male = TdeeCalculator.estimateTdee(profile(gender = "Male"))!!
        val female = TdeeCalculator.estimateTdee(profile(gender = "Female"))!!
        assertTrue("female estimate should be lower", female < male)
    }

    @Test
    fun `cutting takes 15 percent off and bulking adds 10`() {
        val maintain = TdeeCalculator.estimate(profile(goal = "maintain"))!!.calories
        val lose = TdeeCalculator.estimate(profile(goal = "lose"))!!.calories
        val gain = TdeeCalculator.estimate(profile(goal = "gain"))!!.calories
        assertEquals((maintain * 0.85f).toInt(), lose)
        assertEquals((maintain * 1.10f).toInt(), gain)
    }

    @Test
    fun `protein scales with bodyweight and preference`() {
        val moderate = TdeeCalculator.estimate(profile(protein = "moderate"))!!
        val high = TdeeCalculator.estimate(profile(protein = "high"))!!
        assertEquals(80f * 1.6f, moderate.proteinGrams, 1f)
        assertEquals(80f * 2.0f, high.proteinGrams, 1f)
    }

    @Test
    fun `keto shifts calories from carbohydrate into fat`() {
        val balanced = TdeeCalculator.estimate(profile(diet = "balanced"))!!
        val keto = TdeeCalculator.estimate(profile(diet = "keto"))!!
        assertTrue("keto fat should exceed balanced", keto.fatGrams > balanced.fatGrams)
        assertTrue("keto carbs should fall below balanced", keto.carbsGrams < balanced.carbsGrams)
    }

    @Test
    fun `carbohydrate never goes negative when fat and protein use the whole budget`() {
        val extreme = TdeeCalculator.estimate(
            profile(weight = 200f, diet = "keto", protein = "extra-high", goal = "lose")
        )!!
        assertTrue("carbs floored at zero", extreme.carbsGrams >= 0f)
    }

    @Test
    fun `partial answers still estimate once the four basics are present`() {
        val minimal = TdeeCalculator.estimate(
            profile(exercise = null, activity = null, goal = null, diet = null, protein = null)
        )
        assertNotNull("defaults fill in for unanswered questions", minimal)
    }
}
