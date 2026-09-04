// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mise.recipebox.R
import app.mise.recipebox.model.ImagePosition
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.ui.design.LocalMiseColors
import app.mise.recipebox.ui.design.LocalMiseTypography
import app.mise.recipebox.ui.design.MiseShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

enum class MiseIcon { Home, BookOpen, Heart, Folder, Grocery, Search, Plus, Moon, Sun, Server, Clock, ChevronLeft, ChevronRight, X, ArrowRight, Share, Trash, Edit, Check, Minus, Camera, Link, Clipboard, Utensils, ChefHat, Sparkles, Upload, ListChecks, Monitor, Image, Circle }

@Composable
fun MiseIcon(icon: MiseIcon, modifier: Modifier = Modifier.size(19.dp), tint: Color = LocalMiseColors.current.ink, stroke: Float = 1.8f, description: String? = null) {
    Canvas(modifier.semantics { description?.let { contentDescription = it } }) { drawMiseIcon(icon, tint, stroke) }
}

private fun DrawScope.drawMiseIcon(icon: MiseIcon, color: Color, strokeWidth: Float) {
    val unitX = size.width / 20f
    val unitY = size.height / 20f
    fun p(x: Float, y: Float) = Offset(x * unitX, y * unitY)
    val line = Stroke(strokeWidth * minOf(unitX, unitY) / 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(a: Offset, b: Offset) = drawLine(color, a, b, line.width, StrokeCap.Round)
    fun path(points: List<Offset>, closed: Boolean = false) {
        if (points.isEmpty()) return
        val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; if (closed) close() }
        drawPath(path, color, style = line)
    }
    when (icon) {
        MiseIcon.Home -> { path(listOf(p(3f, 9f), p(10f, 3f), p(17f, 9f))); path(listOf(p(5f, 8f), p(5f, 17f), p(15f, 17f), p(15f, 8f))); line(p(8f, 17f), p(8f, 12f)); line(p(8f, 12f), p(12f, 12f)); line(p(12f, 12f), p(12f, 17f)) }
        MiseIcon.BookOpen -> { path(listOf(p(3f, 4f), p(9f, 5f), p(10f, 6f), p(10f, 18f), p(9f, 17f), p(3f, 16f), p(3f, 4f))); path(listOf(p(17f, 4f), p(11f, 5f), p(10f, 6f), p(10f, 18f), p(11f, 17f), p(17f, 16f), p(17f, 4f))) }
        MiseIcon.Heart -> { val shape = Path().apply { moveTo(unitX * 10f, unitY * 17.3f); cubicTo(unitX * 9f, unitY * 16f, unitX * 3f, unitY * 13f, unitX * 3f, unitY * 8f); cubicTo(unitX * 3f, unitY * 5.5f, unitX * 5f, unitY * 4f, unitX * 7.2f, unitY * 4f); cubicTo(unitX * 8.5f, unitY * 4f, unitX * 9.6f, unitY * 4.8f, unitX * 10f, unitY * 5.7f); cubicTo(unitX * 10.4f, unitY * 4.8f, unitX * 11.5f, unitY * 4f, unitX * 12.8f, unitY * 4f); cubicTo(unitX * 15f, unitY * 4f, unitX * 17f, unitY * 5.5f, unitX * 17f, unitY * 8f); cubicTo(unitX * 17f, unitY * 13f, unitX * 11f, unitY * 16f, unitX * 10f, unitY * 17.3f); close() }; drawPath(shape, color, style = line) }
        MiseIcon.Folder -> path(listOf(p(2.5f, 5.5f), p(8f, 5.5f), p(9.5f, 7f), p(17.5f, 7f), p(16.5f, 16.5f), p(3.5f, 16.5f), p(2.5f, 5.5f)), true)
        MiseIcon.Grocery -> { path(listOf(p(4f, 7f), p(16f, 7f), p(15f, 17f), p(5f, 17f), p(4f, 7f)), true); path(listOf(p(7f, 7f), p(7f, 4f), p(13f, 4f), p(13f, 7f))) }
        MiseIcon.Search -> { drawCircle(color, minOf(size.width, size.height) * .32f, p(8f, 8f), style = line); line(p(12.5f, 12.5f), p(17f, 17f)) }
        MiseIcon.Plus -> { line(p(10f, 3f), p(10f, 17f)); line(p(3f, 10f), p(17f, 10f)) }
        MiseIcon.Minus -> line(p(3f, 10f), p(17f, 10f))
        MiseIcon.Moon -> path(listOf(p(16.5f, 12.8f), p(14f, 14f), p(11f, 13f), p(9f, 10f), p(9f, 7f), p(10f, 4f), p(7f, 5f), p(5f, 8f), p(5f, 12f), p(8f, 16f), p(12f, 17f), p(15f, 15f), p(16.5f, 12.8f)))
        MiseIcon.Sun -> { drawCircle(color, minOf(size.width, size.height) * .27f, p(10f, 10f), style = line); for (angle in 0 until 8) { val radians = angle * Math.PI / 4; line(p(10f + kotlin.math.cos(radians).toFloat() * 6f, 10f + kotlin.math.sin(radians).toFloat() * 6f), p(10f + kotlin.math.cos(radians).toFloat() * 8f, 10f + kotlin.math.sin(radians).toFloat() * 8f)) } }
        MiseIcon.Server -> { drawRoundRect(color, p(3f, 3f), Size(unitX * 14f, unitY * 5f), CornerRadius(unitX * 2f), style = line); drawRoundRect(color, p(3f, 12f), Size(unitX * 14f, unitY * 5f), CornerRadius(unitX * 2f), style = line); drawCircle(color, minOf(unitX, unitY) * .7f, p(6f, 5.5f)); drawCircle(color, minOf(unitX, unitY) * .7f, p(6f, 14.5f)) }
        MiseIcon.Clock -> { drawCircle(color, minOf(size.width, size.height) * .34f, p(10f, 10f), style = line); line(p(10f, 6f), p(10f, 10f)); line(p(10f, 10f), p(13f, 12f)) }
        MiseIcon.ChevronLeft -> { line(p(13f, 3f), p(6f, 10f)); line(p(6f, 10f), p(13f, 17f)) }
        MiseIcon.ChevronRight -> { line(p(7f, 3f), p(14f, 10f)); line(p(14f, 10f), p(7f, 17f)) }
        MiseIcon.X -> { line(p(5f, 5f), p(15f, 15f)); line(p(15f, 5f), p(5f, 15f)) }
        MiseIcon.ArrowRight -> { line(p(3f, 10f), p(17f, 10f)); line(p(11f, 4f), p(17f, 10f)); line(p(17f, 10f), p(11f, 16f)) }
        MiseIcon.Share -> { drawCircle(color, minOf(unitX, unitY) * 2f, p(15f, 5f)); drawCircle(color, minOf(unitX, unitY) * 2f, p(5f, 10f)); drawCircle(color, minOf(unitX, unitY) * 2f, p(15f, 15f)); line(p(7f, 9f), p(13f, 6f)); line(p(7f, 11f), p(13f, 14f)) }
        MiseIcon.Trash -> { path(listOf(p(4f, 6f), p(16f, 6f), p(14.5f, 17f), p(5.5f, 17f), p(4f, 6f)), true); line(p(3f, 4f), p(17f, 4f)); line(p(8f, 4f), p(8.5f, 2.5f)); line(p(8.5f, 2.5f), p(11.5f, 2.5f)); line(p(11.5f, 2.5f), p(12f, 4f)) }
        MiseIcon.Edit -> { path(listOf(p(4f, 14f), p(4f, 17f), p(7f, 17f), p(16f, 8f), p(12f, 4f), p(4f, 14f)), true); line(p(10.5f, 5.5f), p(14.5f, 9.5f)) }
        MiseIcon.Check -> { line(p(4f, 10f), p(8f, 14f)); line(p(8f, 14f), p(16f, 5f)) }
        MiseIcon.Camera -> { path(listOf(p(3f, 6f), p(6f, 6f), p(7.5f, 4f), p(12.5f, 4f), p(14f, 6f), p(17f, 6f), p(17f, 16f), p(3f, 16f), p(3f, 6f)), true); drawCircle(color, minOf(size.width, size.height) * .24f, p(10f, 11f), style = line) }
        MiseIcon.Link -> { drawOval(color, topLeft = p(2f, 7f), size = Size(unitX * 9f, unitY * 6f), style = line); drawOval(color, topLeft = p(9f, 7f), size = Size(unitX * 9f, unitY * 6f), style = line); line(p(7f, 10f), p(13f, 10f)) }
        MiseIcon.Clipboard -> { path(listOf(p(5f, 4f), p(15f, 4f), p(15f, 17f), p(5f, 17f), p(5f, 4f)), true); path(listOf(p(8f, 4f), p(8f, 2.5f), p(12f, 2.5f), p(12f, 4f))) }
        MiseIcon.Utensils -> { line(p(6f, 3f), p(6f, 17f)); line(p(4f, 3f), p(4f, 8f)); line(p(8f, 3f), p(8f, 8f)); line(p(4f, 8f), p(8f, 8f)); path(listOf(p(13f, 3f), p(13f, 10f), p(16f, 10f), p(16f, 3f))); line(p(14.5f, 10f), p(14.5f, 17f)) }
        MiseIcon.ChefHat -> { path(listOf(p(3f, 10f), p(3f, 8f), p(5.5f, 7f), p(5.5f, 5f), p(8f, 3f), p(10f, 5f), p(12f, 3f), p(14.5f, 5f), p(14.5f, 7f), p(17f, 8f), p(17f, 10f), p(3f, 10f)), true); line(p(4f, 13f), p(16f, 13f)); line(p(5f, 10f), p(5f, 17f)); line(p(15f, 10f), p(15f, 17f)) }
        MiseIcon.Sparkles -> { line(p(10f, 2f), p(10f, 8f)); line(p(7f, 5f), p(13f, 5f)); line(p(15f, 11f), p(15f, 17f)); line(p(12f, 14f), p(18f, 14f)); line(p(4f, 11f), p(4f, 14f)); line(p(2.5f, 12.5f), p(5.5f, 12.5f)) }
        MiseIcon.Upload -> { line(p(10f, 15f), p(10f, 4f)); line(p(6f, 8f), p(10f, 4f)); line(p(10f, 4f), p(14f, 8f)); path(listOf(p(4f, 12f), p(4f, 17f), p(16f, 17f), p(16f, 12f))) }
        MiseIcon.ListChecks -> { for (row in 0..2) { val yy = 5f + row * 5f; line(p(3f, yy), p(5f, yy + 2f)); line(p(5f, yy + 2f), p(8f, yy - 1f)); line(p(11f, yy + 1f), p(17f, yy + 1f)) } }
        MiseIcon.Monitor -> { drawRoundRect(color, p(2.5f, 3f), Size(unitX * 15f, unitY * 11f), CornerRadius(unitX * 2f), style = line); line(p(7f, 17f), p(13f, 17f)); line(p(10f, 14f), p(10f, 17f)) }
        MiseIcon.Image -> { path(listOf(p(3f, 4f), p(17f, 4f), p(17f, 16f), p(3f, 16f), p(3f, 4f)), true); drawCircle(color, minOf(unitX, unitY) * 2.5f, p(7f, 8f)); path(listOf(p(4f, 14f), p(8f, 10f), p(11f, 13f), p(13f, 11f), p(16f, 14f))) }
        MiseIcon.Circle -> drawCircle(color, minOf(size.width, size.height) * .38f, p(10f, 10f), style = line)
    }
}

@Composable
fun MiseText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalMiseTypography.current.body, color: Color = LocalMiseColors.current.ink, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null) {
    BasicText(text, modifier = modifier, style = style.copy(color = color, textAlign = textAlign ?: style.textAlign), maxLines = maxLines, overflow = overflow)
}

@Composable
fun MiseButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, icon: MiseIcon? = null, enabled: Boolean = true) {
    val colors = LocalMiseColors.current
    Row(modifier.alpha(if (enabled) 1f else .45f).height(42.dp).clip(MiseShapes.button).background(if (primary) colors.deep else Color.Transparent).border(if (primary) 0.dp else 1.dp, colors.line, MiseShapes.button).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { MiseIcon(icon, Modifier.size(17.dp), if (primary) Color.White else colors.ink); Spacer(Modifier.width(7.dp)) }
        MiseText(text, style = LocalMiseTypography.current.label, color = if (primary) Color.White else colors.ink)
    }
}

@Composable
fun MiseIconButton(icon: MiseIcon, onClick: () -> Unit, modifier: Modifier = Modifier.size(40.dp), description: String? = null, filled: Color? = null, iconTint: Color? = null) {
    Box(modifier.clip(CircleShape).then(if (filled != null) Modifier.background(filled) else Modifier).clickable(onClick = onClick), contentAlignment = Alignment.Center) { MiseIcon(icon, Modifier.size(19.dp), iconTint ?: if (filled != null) Color.White else LocalMiseColors.current.ink, description = description) }
}

@Composable
fun MiseTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, embedded: Boolean = false) {
    val colors = LocalMiseColors.current
    val fieldModifier = if (embedded) modifier else modifier.height(if (singleLine) 45.dp else 110.dp).clip(RoundedCornerShape(11.dp)).background(colors.paper).border(1.dp, colors.line, RoundedCornerShape(11.dp)).padding(horizontal = 13.dp, vertical = if (singleLine) 12.dp else 10.dp)
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = singleLine, textStyle = LocalMiseTypography.current.body.copy(color = colors.ink), modifier = fieldModifier, decorationBox = { inner ->
        if (embedded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (value.isBlank()) MiseText(placeholder, color = colors.muted); inner() }
        } else {
            Box { if (value.isBlank()) MiseText(placeholder, color = colors.muted); inner() }
        }
    })
}

@Composable
fun MiseChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMiseColors.current
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) colors.deep else Color.Transparent).border(1.dp, if (selected) colors.deep else colors.line, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp)) { MiseText(text, style = LocalMiseTypography.current.label, color = if (selected) Color.White else colors.ink) }
}

@Composable
fun MiseDivider(modifier: Modifier = Modifier) { Spacer(modifier.fillMaxWidth().height(1.dp).background(LocalMiseColors.current.line)) }

@Composable
fun MiseCheck(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.size(20.dp)) {
    val colors = LocalMiseColors.current
    Box(modifier.clip(CircleShape).background(if (checked) colors.deep else Color.Transparent).border(1.dp, if (checked) colors.deep else colors.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { if (checked) MiseIcon(MiseIcon.Check, Modifier.size(14.dp), Color.White, stroke = 2.3f) }
}

@Composable
fun RecipeImage(recipe: Recipe, modifier: Modifier = Modifier) {
    RecipeImageUrl(recipe.imageUrl, recipe.imagePosition, modifier)
}

@Composable
fun RecipeImageUrl(imageUrl: String?, imagePosition: ImagePosition = ImagePosition.TL, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val remote by produceState<ImageBitmap?>(initialValue = null, imageUrl) { value = imageUrl?.let { url -> withContext(Dispatchers.IO) { runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() } } }
    val sprite = ImageBitmap.imageResource(resources, R.drawable.recipe_sprite)
    Box(modifier.background(LocalMiseColors.current.paleGreen)) {
        if (remote != null) Image(remote!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.fillMaxSize()) {
            val halfW = sprite.width / 2
            val halfH = sprite.height / 2
            val srcX = if (imagePosition == ImagePosition.TR || imagePosition == ImagePosition.BR) halfW else 0
            val srcY = if (imagePosition == ImagePosition.BL || imagePosition == ImagePosition.BR) halfH else 0
            drawImage(sprite, IntOffset(srcX, srcY), IntSize(halfW, halfH), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}

@Composable
fun MiseSheet(modifier: Modifier = Modifier, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalMiseColors.current.scrim).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) { Box(modifier.clip(MiseShapes.sheet).background(LocalMiseColors.current.paper).clickable(enabled = false, onClick = {})) { content() } }
}

@Composable
fun MiseToast(message: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).shadow(10.dp, RoundedCornerShape(12.dp)).background(Color(0xFF152D26)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { MiseIcon(MiseIcon.Check, Modifier.size(16.dp), Color.White, stroke = 2.2f); Spacer(Modifier.width(8.dp)); MiseText(message, style = LocalMiseTypography.current.bodySmall, color = Color.White) }
}
