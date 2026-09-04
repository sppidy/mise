// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.recipebox.data.MiseRepository
import app.mise.recipebox.share.OcrAnalyzer
import app.mise.recipebox.share.SharePayload
import app.mise.recipebox.ui.components.MiseIcon
import app.mise.recipebox.ui.components.MiseText
import app.mise.recipebox.ui.components.MiseToast
import app.mise.recipebox.ui.components.MiseIcon.*
import app.mise.recipebox.ui.design.LocalMiseColors
import app.mise.recipebox.ui.design.LocalMiseTypography
import app.mise.recipebox.ui.design.MiseTheme
import app.mise.recipebox.ui.screens.MainShell
import app.mise.recipebox.ui.screens.Overlay

@Composable
fun MiseApp(
    repository: MiseRepository,
    pendingShare: SharePayload?,
    onShareConsumed: () -> Unit,
    onThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val model = remember(repository) { MiseViewModel(repository, context.applicationContext) }
    val ui by model.state.collectAsStateWithLifecycle()
    val dark = when (ui.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(pendingShare) {
        pendingShare?.let { model.consumeShare(it, OcrAnalyzer(context), onShareConsumed) }
    }
    LaunchedEffect(dark) { onThemeChanged(dark) }
    BackHandler(enabled = ui.sheet != null) {
        if (ui.draft != null && ui.sheet in setOf("import", "preview")) model.discardDraft()
        else model.closeSheet()
    }
    MiseTheme(dark) {
        Box(Modifier.fillMaxSize().background(LocalMiseColors.current.cream)) {
            if (ui.initializing) SplashScreen()
            else MainShell(ui, model, dark)
            if (ui.sheet != null && !ui.initializing) Overlay(ui, model, model::closeSheet)
            ui.toast?.let { RowToast(it) }
        }
    }
}

@Composable
private fun SplashScreen() {
    val colors = LocalMiseColors.current
    Box(Modifier.fillMaxSize().background(colors.deep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.padding(bottom = 2.dp), contentAlignment = Alignment.Center) { MiseText("mise", style = LocalMiseTypography.current.title.copy(fontSize = 39.sp, lineHeight = 40.sp), color = Color.White) }
            MiseIcon(ChefHat, Modifier.size(23.dp), colors.yellow)
            MiseText("Your kitchen, all in one place.", style = LocalMiseTypography.current.bodySmall, color = Color(0xFFB9CCC4))
        }
    }
}

@Composable
private fun RowToast(message: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 30.dp), contentAlignment = Alignment.BottomCenter) { MiseToast(message) }
}
