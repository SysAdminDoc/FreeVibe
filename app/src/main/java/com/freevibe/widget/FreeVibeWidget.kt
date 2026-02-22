package com.freevibe.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.WallpaperApplier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Colors ────────────────────────────────────────────────────────

private val WidgetBg = ColorProvider(Color(0xFF0A0A0F))
private val Primary = ColorProvider(Color(0xFF7C5CFC))
private val Secondary = ColorProvider(Color(0xFF00D4AA))
private val SurfaceTone = ColorProvider(Color(0xFF1A1A24))
private val TextDim = ColorProvider(Color(0xFF888888))
private val White = ColorProvider(Color.White)

// ── Widget ────────────────────────────────────────────────────────

class FreeVibeWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(200.dp, 110.dp)
        private val LARGE = DpSize(200.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(LocalSize.current) }
    }
}

@Composable
private fun WidgetContent(size: DpSize) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = "FreeVibe",
                style = TextStyle(
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
            )

            Spacer(GlanceModifier.height(8.dp))

            // Shuffle button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Primary)
                    .cornerRadius(12.dp)
                    .clickable(actionRunCallback<ShuffleWallpaperAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Shuffle",
                    style = TextStyle(
                        color = White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                )
            }

            // Home/Lock quick actions for wider widgets
            if (size.width >= 200.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    QuickActionBtn("Home", GlanceModifier.defaultWeight(), actionRunCallback<ApplyHomeAction>())
                    Spacer(GlanceModifier.width(8.dp))
                    QuickActionBtn("Lock", GlanceModifier.defaultWeight(), actionRunCallback<ApplyLockAction>())
                }
            }

            // Open app link for taller widgets
            if (size.height >= 200.dp) {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "Tap to open app",
                    style = TextStyle(color = TextDim, fontSize = 11.sp),
                    modifier = GlanceModifier.clickable(actionRunCallback<OpenAppAction>()),
                )
            }
        }
    }
}

@Composable
private fun QuickActionBtn(
    label: String,
    modifier: GlanceModifier,
    action: androidx.glance.action.Action,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(SurfaceTone)
            .cornerRadius(8.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(color = Secondary, fontSize = 12.sp),
        )
    }
}

// ── Hilt EntryPoint ───────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun wallpaperRepository(): WallpaperRepository
    fun wallpaperApplier(): WallpaperApplier
}

private fun getEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

// ── Actions ───────────────────────────────────────────────────────

class ShuffleWallpaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.BOTH)
        FreeVibeWidget().update(context, glanceId)
    }
}

class ApplyHomeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.HOME)
    }
}

class ApplyLockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.LOCK)
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private suspend fun applyRandom(context: Context, target: WallpaperTarget) {
    withContext(Dispatchers.IO) {
        try {
            val ep = getEntryPoint(context)
            val wp = ep.wallpaperRepository().getWallhaven(page = 1).items.randomOrNull() ?: return@withContext
            ep.wallpaperApplier().applyFromUrl(wp.fullUrl, target)
        } catch (_: Exception) {}
    }
}

// ── Receiver ──────────────────────────────────────────────────────

class FreeVibeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreeVibeWidget()
}
