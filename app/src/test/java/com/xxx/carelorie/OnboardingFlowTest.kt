package com.xxx.carelorie

import com.xxx.carelorie.data.UserProfile
import com.xxx.carelorie.data.onboarding.OnboardingFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    private val fresh = UserProfile(userId = "u1")

    @Test
    fun `a brand new user starts at the very first question`() {
        assertEquals(0, OnboardingFlow.resumeIndex(fresh))
    }

    @Test
    fun `height and weight are asked before the expenditure summary`() {
        val keys = OnboardingFlow.steps.map { it.key }
        assertTrue("height missing from flow", "height" in keys)
        assertTrue("weight missing from flow", "weight" in keys)
        assertTrue(keys.indexOf("height") < keys.indexOf("expenditure"))
        assertTrue(keys.indexOf("weight") < keys.indexOf("expenditure"))
    }

    @Test
    fun `height and weight count as unanswered for a fresh profile`() {
        val height = OnboardingFlow.steps.first { it.key == "height" }
        val weight = OnboardingFlow.steps.first { it.key == "weight" }
        assertTrue("height should be unanswered", !height.isAnswered(fresh))
        assertTrue("weight should be unanswered", !weight.isAnswered(fresh))
    }

    // The reported symptom: setup opened without ever asking height or weight.
    @Test
    fun `a profile that already has height and weight resumes past them`() {
        val partly = fresh.copy(name = "N", gender = "Male", birthday = "01/01/1990",
            height = "180", weight = 80f)
        val resumed = OnboardingFlow.resumeIndex(partly)
        val keys = OnboardingFlow.steps.map { it.key }
        assertTrue(
            "resumed at ${keys[resumed]} - height/weight are skipped once already set",
            resumed > keys.indexOf("weight")
        )
    }
}
