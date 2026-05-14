package com.freevibe.ui.screens.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.data.repository.CreatorProfileDashboard
import com.freevibe.data.repository.CreatorProfileRepository
import com.freevibe.data.repository.CreatorStats
import com.freevibe.data.repository.CreatorUploadRef
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.GlassCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CreatorProfileUiState(
    val isLoading: Boolean = true,
    val dashboard: CreatorProfileDashboard? = null,
    val error: String? = null,
    val actionInFlightCreatorId: String? = null,
)

@HiltViewModel
class CreatorProfileViewModel @Inject constructor(
    private val repository: CreatorProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CreatorProfileUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getDashboard() }
                .onSuccess { dashboard ->
                    _state.update {
                        it.copy(isLoading = false, dashboard = dashboard, error = null)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(isLoading = false, error = e.message ?: "Creator profile could not load")
                    }
                }
        }
    }

    fun follow(creator: CreatorStats) {
        updateFollow(creator, follow = true)
    }

    fun unfollow(creator: CreatorStats) {
        updateFollow(creator, follow = false)
    }

    private fun updateFollow(creator: CreatorStats, follow: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(actionInFlightCreatorId = creator.creatorId, error = null) }
            val result = if (follow) {
                repository.followCreator(creator.creatorId, creator.label)
            } else {
                repository.unfollowCreator(creator.creatorId)
            }
            result
                .onSuccess {
                    _state.update { it.copy(actionInFlightCreatorId = null) }
                    refresh()
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(
                            actionInFlightCreatorId = null,
                            error = e.message ?: "Follow action failed",
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorProfileScreen(
    onBack: () -> Unit,
    viewModel: CreatorProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creator profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && dashboard == null -> {
                    AuraStateCard(
                        icon = Icons.Default.Person,
                        title = "Loading creator profile",
                        description = "Aura is gathering your uploads, votes, follows, and community leaderboard.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                state.error != null && dashboard == null -> {
                    AuraStateCard(
                        icon = Icons.Default.Groups,
                        title = "Creator profile unavailable",
                        description = state.error ?: "Try again in a moment.",
                        primaryAction = AuraStateAction("Retry", Icons.Default.Refresh, viewModel::refresh),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                dashboard != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            CreatorSummaryCard(dashboard)
                        }
                        if (state.error != null) {
                            item {
                                AssistChip(
                                    onClick = viewModel::refresh,
                                    label = { Text(state.error ?: "Refresh creator profile") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                        item {
                            SectionHeader("Top creators", Icons.Default.Leaderboard)
                        }
                        if (dashboard.topCreators.isEmpty()) {
                            item {
                                EmptyCreatorSection("No creator uploads yet")
                            }
                        } else {
                            items(dashboard.topCreators, key = { "top_${it.creatorId}" }) { creator ->
                                CreatorRow(
                                    creator = creator,
                                    isCurrentUser = creator.creatorId == dashboard.currentCreator.creatorId,
                                    actionInFlight = state.actionInFlightCreatorId == creator.creatorId,
                                    onFollow = { viewModel.follow(creator) },
                                    onUnfollow = { viewModel.unfollow(creator) },
                                )
                            }
                        }
                        item {
                            SectionHeader("Following", Icons.Default.Favorite)
                        }
                        if (dashboard.followedCreators.isEmpty()) {
                            item {
                                EmptyCreatorSection("Follow creators from the leaderboard to track their new uploads.")
                            }
                        } else {
                            items(dashboard.followedCreators, key = { "followed_${it.creatorId}" }) { creator ->
                                CreatorRow(
                                    creator = creator,
                                    isCurrentUser = false,
                                    actionInFlight = state.actionInFlightCreatorId == creator.creatorId,
                                    onFollow = { viewModel.follow(creator) },
                                    onUnfollow = { viewModel.unfollow(creator) },
                                )
                            }
                        }
                        item {
                            SectionHeader("New from follows", Icons.Default.Upload)
                        }
                        if (dashboard.followedUploads.isEmpty()) {
                            item {
                                EmptyCreatorSection("Followed creator uploads will appear here.")
                            }
                        } else {
                            items(dashboard.followedUploads, key = { it.stableKey }) { upload ->
                                UploadRow(upload)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorSummaryCard(dashboard: CreatorProfileDashboard) {
    val creator = dashboard.currentCreator
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        highlightHeight = 72.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(creator.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dashboard.authLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CreatorMetric("Uploads", creator.uploadCount.toString(), Modifier.weight(1f))
            CreatorMetric("Votes", creator.totalVotes.toString(), Modifier.weight(1f))
            CreatorMetric("Saved", creator.favoritesCount.toString(), Modifier.weight(1f))
        }
        if (!dashboard.googleSignInAvailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Google sign-in needs a Firebase OAuth client before it can be enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatorMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreatorRow(
    creator: CreatorStats,
    isCurrentUser: Boolean,
    actionInFlight: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(creator.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${creator.uploadCount} uploads - ${creator.totalVotes} votes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isCurrentUser) {
                TextButton(
                    onClick = if (creator.isFollowed) onUnfollow else onFollow,
                    enabled = !actionInFlight,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (actionInFlight) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (creator.isFollowed) "Following" else "Follow")
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadRow(upload: CreatorUploadRef) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    upload.contentType.take(1).uppercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(upload.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${upload.creatorLabel} - ${upload.votes} votes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyCreatorSection(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
