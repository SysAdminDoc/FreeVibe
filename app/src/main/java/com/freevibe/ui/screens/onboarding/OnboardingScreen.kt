package com.freevibe.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val isComplete: Boolean = false,
    val selectedStyles: Set<String> = emptySet(),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun toggleStyle(style: String) {
        _state.update { st ->
            val updated = if (style in st.selectedStyles) st.selectedStyles - style else st.selectedStyles + style
            st.copy(selectedStyles = updated)
        }
    }

    fun complete() {
        viewModelScope.launch {
            prefs.setUserStyles(_state.value.selectedStyles.joinToString(","))
            _state.update { it.copy(isComplete = true) }
        }
    }

    fun skip() {
        _state.update { it.copy(isComplete = true) }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        radius = 1200f,
                    ),
                ),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingTopBar(
                currentPage = pagerState.currentPage,
                totalPages = 4,
                onSkip = viewModel::skip,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturesPage()
                    2 -> StylePickerPage(state.selectedStyles) { viewModel.toggleStyle(it) }
                    3 -> ReadyPage()
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { i ->
                    val width by animateDpAsState(
                        targetValue = if (pagerState.currentPage == i) 28.dp else 8.dp,
                        animationSpec = tween(durationMillis = 220),
                        label = "indicator_width",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(width)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Back") }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.complete()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(if (pagerState.currentPage == 3) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentPage: Int,
    totalPages: Int,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightPill(
            label = "Step ${currentPage + 1} of $totalPages",
            icon = Icons.Default.AutoAwesome,
            tint = MaterialTheme.colorScheme.secondary,
        )
        if (currentPage < totalPages - 1) {
            TextButton(onClick = onSkip) {
                Text("Skip setup", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    PageLayout(
        eyebrow = "Personalization, without the clutter",
        icon = Icons.Default.Wallpaper,
        iconColor = MaterialTheme.colorScheme.primary,
        title = "Welcome to Aura",
        description = "The open-source way to personalize your Android device with wallpapers, video wallpapers, ringtones, and sounds from YouTube plus open media catalogs. No accounts, no ads, no setup.",
        badges = listOf(
            Icons.Default.LockOpen to "No account",
            Icons.Default.Block to "No ads",
            Icons.Default.Verified to "Open source",
        ),
        content = {
            FeatureRow(
                icon = Icons.Default.AutoAwesome,
                title = "A calmer discovery feed",
                subtitle = "Curated sources, style preferences, and smarter quality ranking keep browsing intentional.",
            )
            FeatureRow(
                icon = Icons.Default.VideoLibrary,
                title = "Motion and sound in one place",
                subtitle = "Live wallpapers, trimmed clips, ringtones, and ambient extras live under one roof.",
            )
            FeatureRow(
                icon = Icons.Default.Tune,
                title = "You stay in control",
                subtitle = "Tweak sources, automation, previews, and battery tradeoffs whenever you want.",
            )
        },
    )
}

@Composable
private fun FeaturesPage() {
    val features = listOf(
        Triple(Icons.Default.Wallpaper, "HD/4K Wallpapers", "5 sources: Wallhaven, Pexels, Pixabay, Reddit, Bing"),
        Triple(Icons.Default.VideoLibrary, "Video Wallpapers", "Pexels, Pixabay loops, YouTube, Reddit cinemagraphs"),
        Triple(Icons.Default.MusicNote, "Ringtones & Sounds", "YouTube, Freesound, Openverse, Audius, ccMixter, trim & fade editor"),
        Triple(Icons.Default.Schedule, "Smart Scheduler", "Auto-rotate by interval, source, or time of day"),
        Triple(Icons.Default.Cloud, "Weather Effects", "Rain, snow, fog overlay from real-time weather"),
        Triple(Icons.Default.DarkMode, "AMOLED Editor", "Black crush, vignette, grain, warmth + 10 presets"),
    )

    PageLayout(
        eyebrow = "What you get",
        icon = Icons.Default.AutoAwesome,
        iconColor = MaterialTheme.colorScheme.secondary,
        title = "A full personalization toolkit",
        description = "Aura combines discovery, motion, sound, and automation so your device feels consistent instead of stitched together from separate apps.",
        badges = listOf(
            Icons.Default.Wallpaper to "Wallpapers",
            Icons.Default.VideoLibrary to "Video loops",
            Icons.Default.NotificationsActive to "Sound tools",
        ),
        content = {
            features.forEachIndexed { index, (icon, title, subtitle) ->
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    anim.animateTo(1f, animationSpec = tween(400, delayMillis = index * 80))
                }
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = anim.value
                        translationX = (1f - anim.value) * -60f
                    },
                ) {
                    FeatureRow(icon, title, subtitle)
                }
            }
        },
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(10.dp)
                    .size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePickerPage(selectedStyles: Set<String>, onToggle: (String) -> Unit) {
    val styles = listOf(
        OnboardingStyleOption("minimal", "Minimal", "Clean layouts with breathing room", Icons.Default.CropSquare, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("amoled", "AMOLED", "Deep blacks for dim, high-contrast screens", Icons.Default.DarkMode, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("nature", "Nature", "Landscapes, foliage, and organic textures", Icons.Default.Landscape, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("space", "Space", "Celestial scenes, stars, and nebula energy", Icons.Default.Public, MaterialTheme.colorScheme.tertiary),
        OnboardingStyleOption("anime", "Anime", "Illustrated scenes and stylized characters", Icons.Default.Movie, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("abstract", "Abstract", "Shape-driven art, gradients, and light play", Icons.Default.AutoAwesome, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("neon", "Neon", "Bold glow, nightlife, and cyber accents", Icons.Default.Bolt, MaterialTheme.colorScheme.tertiary),
        OnboardingStyleOption("city", "City", "Architecture, streets, and urban atmosphere", Icons.Default.LocationCity, MaterialTheme.colorScheme.primary),
        OnboardingStyleOption("gradient", "Gradient", "Soft tonal blends and color-field backgrounds", Icons.Default.BlurOn, MaterialTheme.colorScheme.secondary),
        OnboardingStyleOption("dark", "Dark", "Moody, low-light, and cinematic compositions", Icons.Default.Brightness3, MaterialTheme.colorScheme.tertiary),
    )

    PageLayout(
        eyebrow = "Tailor the feed",
        icon = Icons.Default.Tune,
        iconColor = MaterialTheme.colorScheme.primary,
        title = "What should Aura lean toward?",
        description = "Pick a few looks you want to see more often in Discover. You can adjust this anytime in Settings.",
        badges = emptyList(),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Style profile",
                    style = MaterialTheme.typography.titleMedium,
                )
                HighlightPill(
                    label = if (selectedStyles.isEmpty()) "Optional" else "${selectedStyles.size} selected",
                    icon = Icons.Default.CheckCircle,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))

            styles.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { option ->
                        val selected = option.id in selectedStyles
                        Surface(
                            onClick = { onToggle(option.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(132.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = if (selected) option.tint.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) option.tint.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = option.tint.copy(alpha = 0.14f),
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = option.tint,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(18.dp),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        },
    )
}

@Composable
private fun ReadyPage() {
    PageLayout(
        eyebrow = "Ready to explore",
        icon = Icons.Default.Celebration,
        iconColor = MaterialTheme.colorScheme.secondary,
        title = "You're all set",
        description = "Everything works out of the box. Start browsing wallpapers and sounds, or add the home screen widget for quick access.",
        badges = listOf(
            Icons.Default.Wallpaper to "Discover",
            Icons.Default.MusicNote to "Sounds",
            Icons.Default.Widgets to "Widget",
        ),
        content = {
            FeatureRow(
                icon = Icons.Default.Explore,
                title = "Start with Discover",
                subtitle = "Aura will already bias results toward higher-quality, phone-friendly picks.",
            )
            FeatureRow(
                icon = Icons.Default.Settings,
                title = "Refine the details later",
                subtitle = "Rotation, weather effects, previews, sources, and battery tradeoffs stay easy to tweak.",
            )
            FeatureRow(
                icon = Icons.Default.Widgets,
                title = "Add quick access",
                subtitle = "The home screen widget gives you instant shuffle and wallpaper actions without opening the app.",
            )
        },
    )
}

private data class OnboardingStyleOption(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageLayout(
    eyebrow: String,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    badges: List<Pair<ImageVector, String>> = emptyList(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HighlightPill(label = eyebrow, icon = Icons.Default.AutoAwesome, tint = iconColor)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    badges.forEach { (badgeIcon, label) ->
                        HighlightPill(
                            label = label,
                            icon = badgeIcon,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            if (content != null) {
                Spacer(Modifier.height(24.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}
