// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.data.importFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiseApiTest {
    @Test fun usesBoundedHumanReadableJsonError() {
        assertEquals(
            "No recipe was found in the caption or public pinned comments",
            importFailureMessage(422, "{\"error\":\"No recipe was found in the caption or public pinned comments\"}"),
        )
        val bounded = importFailureMessage(422, "{\"error\":\"${"x".repeat(500)}\"}")
        assertEquals(300, bounded.length)
        assertTrue(bounded.endsWith("…"))
    }

    @Test fun refusesHtmlInvalidAndOversizedErrorBodies() {
        val fallback = "Request failed with HTTP 422"
        assertEquals(fallback, importFailureMessage(422, "<html>upstream failed</html>"))
        assertEquals(fallback, importFailureMessage(422, "not-json"))
        assertEquals(fallback, importFailureMessage(422, "{\"error\":\"${"x".repeat(17_000)}\"}"))
    }
}
