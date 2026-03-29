package com.freevibe.widget

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Colors ────────────────────────────────────────────────────────

private val WidgetBg = ColorProvider(Color(0xFF0A0A0F))
private val Primary = ColorProvider(Color(0xFF7C5CFC))
private val Secondary = ColorProvider(Color(0xFF00D4AA))
private val Tertiary = ColorProvider(Color(0xFFFF6B9D))
private val SurfaceTone = ColorProvider(Color(0xFF1A1A24))
private val TextDim = ColorProvider(Color(0xFF888888))
private val White = ColorProvider(Color.White)

private const val WIDGET_PREFS = "freevibe_widget"
private const val LAST_SHUFFLE_KEY = "last_shuffle_time"
private const val SHUFFLE_COUNT_KEY = "shuffle_count"

// ── Widget ────────────────────────────────────────────────────────

class FreeVibeWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(200.dp, 110.dp)
        private val LARGE = DpSize(200.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val lastShuffle = prefs.getLong(LAST_SHUFFLE_KEY, 0)
        val shuffleCount = prefs.getInt(SHUFFLE_COUNT_KEY, 0)

        provideContent {
            WidgetContent(
                size = LocalSize.current,
                lastShuffleTime = lastShuffle,
                shuffleCount = shuffleCount,
            )
        }
    }
}

@Composable
private fun WidgetContent(
    size: DpSize,
    lastShuffleTime: Long,
    shuffleCount: Int,
) {
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
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = "Aura",
                    style = TextStyle(
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                )
                if (shuffleCount > 0 && size.width >= 200.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "$shuffleCount shuffled",
                        style = TextStyle(color = TextDim, fontSize = 10.sp),
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            // Last shuffle timestamp
            if (lastShuffleTime > 0 && size.height >= 200.dp) {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastShuffleTime))
                Text(
                    text = "Last: $timeStr",
                    style = TextStyle(color = TextDim, fontSize = 10.sp),
                )
                Spacer(GlanceModifier.height(4.dp))
            }

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

            // Home/Lock/Skip quick actions for wider widgets
            if (size.width >= 200.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    QuickActionBtn("Home", GlanceModifier.defaultWeight(), actionRunCallback<ApplyHomeAction>())
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Lock", GlanceModifier.defaultWeight(), actionRunCallback<ApplyLockAction>())
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Faves", GlanceModifier.defaultWeight(), actionRunCallback<OpenFavoritesAction>(), Tertiary)
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    QuickActionBtn("Pixabay", GlanceModifier.defaultWeight(), actionRunCallback<ShufflePixabayAction>(), ColorProvider(Color(0xFF00AB6C)))
                    Spacer(GlanceModifier.width(4.dp))
                    QuickActionBtn("Reddit", GlanceModifier.defaultWeight(), actionRunCallback<ShuffleRedditAction>(), ColorProvider(Color(0xFFFF4500)))
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
    color: ColorProvider = Secondary,
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
            style = TextStyle(color = color, fontSize = 11.sp),
        )
    }
}

// ── Hilt EntryPoint ───────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun wallpaperRepository(): WallpaperRepository
    fun redditRepository(): RedditRepository
    fun wallpaperApplier(): WallpaperApplier
    fun wallpaperHistoryManager(): WallpaperHistoryManager
}

private fun getEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

private fun updateWidgetStats(context: Context) {
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(LAST_SHUFFLE_KEY, System.currentTimeMillis())
        .putInt(SHUFFLE_COUNT_KEY,
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getInt(SHUFFLE_COUNT_KEY, 0) + 1
        )
        .apply()
}

// ── Actions ───────────────────────────────────────────────────────

class ShuffleWallpaperAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.BOTH)
        updateWidgetStats(context)
        FreeVibeWidget().update(context, glanceId)
    }
}

class ApplyHomeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.HOME)
        updateWidgetStats(context)
        FreeVibeWidget().update(context, glanceId)
    }
}

class ApplyLockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyRandom(context, WallpaperTarget.LOCK)
        updateWidgetStats(context)
        FreeVibeWidget().update(context, glanceId)
    }
}

class OpenFavoritesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("navigate_to", "favorites")
            context.startActivity(intent)
        }
    }
}

class ShufflePixabayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyFromSource(context, "pixabay", WallpaperTarget.BOTH)
        updateWidgetStats(context)
        FreeVibeWidget().update(context, glanceId)
    }
}

class ShuffleRedditAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        applyFromSource(context, "reddit", WallpaperTarget.BOTH)
        updateWidgetStats(context)
        FreeVibeWidget().update(context, glanceId)
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

private suspend fun applyFromSource(context: Context, source: String, target: WallpaperTarget) {
    withContext(Dispatchers.IO) {
        try {
            val ep = getEntryPoint(context)
            val items = when (source) {
                "pixabay" -> ep.wallpaperRepository().getPixabay(page = (1..5).random()).items
                "reddit" -> ep.redditRepository().getMultiSubreddit().items
                else -> ep.wallpaperRepository().getDiscover(page = 1).items
            }
            val wp = items.randomOrNull()
            if (wp == null) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "No wallpapers available", Toast.LENGTH_SHORT).show() }
                return@withContext
            }
            ep.wallpaperApplier().applyFromUrl(wp.fullUrl, target)
            ep.wallpaperHistoryManager().record(wp, target)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message?.take(50)}", Toast.LENGTH_SHORT).show() }
        }
    }
}

private suspend fun applyRandom(context: Context, target: WallpaperTarget) {
    withContext(Dispatchers.IO) {
        try {
            val ep = getEntryPoint(context)
            val wp = ep.wallpaperRepository().getWallhaven(page = 1).items.randomOrNull()
            if (wp == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No wallpapers available", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            ep.wallpaperApplier().applyFromUrl(wp.fullUrl, target)
            ep.wallpaperHistoryManager().record(wp, target)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Shuffle failed: ${e.message?.take(50)}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ── Receiver ──────────────────────────────────────────────────────

class FreeVibeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreeVibeWidget()
}
