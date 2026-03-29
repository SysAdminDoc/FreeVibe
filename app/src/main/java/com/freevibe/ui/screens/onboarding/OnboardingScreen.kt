package com.freevibe.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.freevibe.data.local.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val isComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun complete() {
        _state.update { it.copy(isComplete = true) }
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
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0F), Color(0xFF12121A))
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip button (hidden on last page)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = { viewModel.skip() }) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeaturesPage()
                    2 -> ReadyPage()
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == i) 10.dp else 6.dp)
                            .clip(CircleShape)
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
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Back") }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.complete()
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (pagerState.currentPage == 2) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    PageLayout(
        icon = Icons.Default.Wallpaper,
        iconColor = MaterialTheme.colorScheme.primary,
        title = "Welcome to Aura",
        description = "The open-source way to personalize your Android device with wallpapers, video wallpapers, ringtones, and sounds. YouTube-powered. No accounts, no ads, no setup.",
    )
}

@Composable
private fun FeaturesPage() {
    val features = listOf(
        Triple(Icons.Default.Wallpaper, "HD/4K Wallpapers", "6 sources: Wallhaven, Pexels, Pixabay, Reddit, Unsplash, Bing"),
        Triple(Icons.Default.VideoLibrary, "Video Wallpapers", "Pexels, Pixabay loops, YouTube, Reddit cinemagraphs"),
        Triple(Icons.Default.MusicNote, "Ringtones & Sounds", "YouTube + Internet Archive, trim & fade editor"),
        Triple(Icons.Default.Schedule, "Smart Scheduler", "Auto-rotate by interval, source, or time of day"),
        Triple(Icons.Default.Cloud, "Weather Effects", "Rain, snow, fog overlay from real-time weather"),
        Triple(Icons.Default.DarkMode, "AMOLED Editor", "Black crush, vignette, grain, warmth + 10 presets"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("What you get", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

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
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyPage() {
    PageLayout(
        icon = Icons.Default.Celebration,
        iconColor = MaterialTheme.colorScheme.secondary,
        title = "You're all set",
        description = "Everything works out of the box. Start browsing wallpapers and sounds, or add the home screen widget for quick access.",
    )
}

@Composable
private fun PageLayout(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
