package com.priyabrat.medusa.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSelectionTest {
    private val a = Profile("a", "A", 0)
    private val b = Profile("b", "B", 1)

    @Test fun signingOutLastAccountLeavesNoActiveProfile() {
        val result = Profiles(listOf(a), "a").withoutLocal("a")
        assertEquals(emptyList<Profile>(), result.list)
        assertEquals("", result.active)
    }

    @Test fun signingOutActiveAccountSelectsAnotherWithoutDeletingIt() {
        val result = Profiles(listOf(a, b), "a").withoutLocal("a")
        assertEquals(listOf(b), result.list)
        assertEquals("b", result.active)
    }

    @Test fun restoringAccountSelectsItWithoutDuplicatingExistingProfile() {
        val updated = a.copy(name = "Updated")
        val result = Profiles(listOf(a, b), "b").withSelected(updated)
        assertEquals(listOf(updated, b), result.list)
        assertEquals("a", result.active)
    }

    @Test fun syncDoesNotRestoreSignedOutOrUnrelatedProfiles() {
        val result = Profiles(listOf(a), "a").refreshVisible(listOf(a.copy(name = "Cloud"), b), emptySet(), emptySet())
        assertEquals(listOf(a.copy(name = "Cloud")), result)
    }
}
