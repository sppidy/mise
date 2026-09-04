// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import app.mise.recipebox.data.MiseRepository
import app.mise.recipebox.share.SharePayload
import app.mise.recipebox.share.ShareReceiver
import app.mise.recipebox.ui.MiseApp

class MainActivity : ComponentActivity() {
    private lateinit var receiver: ShareReceiver
    private lateinit var repository: MiseRepository
    private var pendingShare by mutableStateOf<SharePayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = ShareReceiver(this)
        repository = MiseRepository(applicationContext)
        pendingShare = receiver.capture(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBars(dark = false)
        setContent {
            MiseApp(
                repository = repository,
                pendingShare = pendingShare,
                onShareConsumed = { pendingShare = null },
                onThemeChanged = { dark -> applySystemBars(dark) },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiver.capture(intent)?.let { pendingShare = it }
    }

    private fun applySystemBars(dark: Boolean) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
