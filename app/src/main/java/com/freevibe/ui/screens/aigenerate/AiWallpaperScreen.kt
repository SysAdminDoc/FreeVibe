package com.freevibe.ui.screens.aigenerate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.AiStyle
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.components.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWallpaperScreen(
    onBack: () -> Unit,
    viewModel: AiWallpaperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiKey by viewModel.stabilityAiKey.collectAsStateWithLifecycle()

    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var showApiKeyField by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) showApiKeyField = true
    }
    LaunchedEffect(state.applySuccess) {
        state.applySuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Header card ──────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                highlightHeight = 100.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        HighlightPill(
                            label = "Stability AI",
                            icon = Icons.Default.AutoAwesome,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Generate AI Wallpaper", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Describe your perfect wallpaper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { showApiKeyField = !showApiKeyField },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "API key settings",
                            modifier = Modifier.size(18.dp),
                            tint = if (apiKey.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            // ── API key field ────────────────────────────────────────────
            AnimatedVisibility(
                visible = showApiKeyField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Stability AI API Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localApiKey,
                        onValueChange = { localApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("sk-...") },
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Get a free key at platform.stability.ai",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.saveApiKey(localApiKey)
                                showApiKeyField = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Save", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Prompt input ─────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "Prompt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.setPrompt(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = {
                        Text("A misty Japanese forest at dawn, soft golden light filtering through ancient cedar trees, 4K, ultra detail…")
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Default,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    maxLines = 8,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${state.prompt.length}/500",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // ── Style picker ─────────────────────────────────────────────
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    "Style",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style,
                            onClick = { viewModel.setStyle(style) },
                            label = {
                                Text(style.label, style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            }

            // ── Generate button ──────────────────────────────────────────
            Button(
                onClick = { viewModel.generate(localApiKey.ifBlank { apiKey }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(52.dp),
                enabled = !state.isGenerating && !state.isApplying,
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Generating…")
                } else {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Wallpaper")
                }
            }

            // ── Generating shimmer placeholder ───────────────────────────
            if (state.isGenerating) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                )
            }

            // ── Result image + actions ───────────────────────────────────
            AnimatedVisibility(
                visible = state.result != null && !state.isGenerating,
                enter = fadeIn() + expandVertically(),
            ) {
                val wallpaper = state.result
                if (wallpaper != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubcomposeAsyncImage(
                            model = wallpaper.thumbnailUrl,
                            contentDescription = "Generated wallpaper",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop,
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> {
                                    ShimmerBox(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(18.dp),
                                    )
                                }
                                is AsyncImagePainter.State.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.saveToFavorites(state.prompt) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSaved && !state.isApplying,
                            ) {
                                Icon(
                                    if (state.isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.isSaved) "Saved" else "Save")
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { showTargetMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isApplying,
                                ) {
                                    if (state.isApplying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Wallpaper,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Set Wallpaper")
                                    }
                                }
                                DropdownMenu(
                                    expanded = showTargetMenu,
                                    onDismissRequest = { showTargetMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Home screen") },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.HOME)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Lock screen") },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.LOCK)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Both") },
                                        onClick = {
                                            showTargetMenu = false
                                            viewModel.applyWallpaper(WallpaperTarget.BOTH)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
