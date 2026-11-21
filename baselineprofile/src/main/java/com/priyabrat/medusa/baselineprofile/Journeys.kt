package com.priyabrat.medusa.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE = "com.priyabrat.medusa"

/** Home is up once the carousel's title line is on screen. */
fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.textContains("TONIGHT")), 10_000)
    device.waitForIdle()
}

/** Up and down through the Home rows with the D-pad, like Prince does with the remote. */
fun MacrobenchmarkScope.scrollHomeRows(rows: Int = 12) {
    repeat(rows) { device.pressDPadDown(); Thread.sleep(250) }
    repeat(2) { device.pressDPadRight(); Thread.sleep(250) }
    repeat(rows) { device.pressDPadUp(); Thread.sleep(250) }
}

/**
 * Visit the other tabs and scroll each a little, so their code is in the profile too.
 * Starts with focus on the Home tab (where [scrollHomeRows] ends); Right then OK opens the next tab,
 * and the app puts focus back on that tab.
 */
fun MacrobenchmarkScope.visitTabs() {
    repeat(5) {
        device.pressDPadRight(); Thread.sleep(200)
        device.pressDPadCenter(); Thread.sleep(1_000)
        repeat(4) { device.pressDPadDown(); Thread.sleep(250) }
        repeat(6) { device.pressDPadUp(); Thread.sleep(250) }
    }
}
