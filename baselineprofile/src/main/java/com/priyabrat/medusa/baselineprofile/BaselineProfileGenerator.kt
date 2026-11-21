package com.priyabrat.medusa.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code Medusa runs on start, while scrolling Home and on the other tabs.
 * Run with ./gradlew :app:generateReleaseBaselineProfile; the result lands in
 * app/src/release/generated/baselineProfiles and is shipped inside the APK.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        scrollHomeRows()
        visitTabs()
    }
}
