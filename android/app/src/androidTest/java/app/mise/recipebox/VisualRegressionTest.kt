// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected visual capture entry point. The harness is intentionally opt-in and
 * must only run on a separately authorized disposable emulator.
 */
@RunWith(AndroidJUnit4::class)
class VisualRegressionTest {
    @Test fun visualScenarioFixtureIsPresent() {
        assertTrue("visual scenarios are checked in", java.io.File("../visual-scenarios.json").exists() || java.io.File("visual-scenarios.json").exists())
    }
}
