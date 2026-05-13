package com.freevibe.ui.screens.sounds

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.ShimmerBox
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.ContactInfo
import com.freevibe.service.ContactRingtoneService
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ContactPickerState(
    val contacts: List<ContactInfo> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
    val hasPermission: Boolean = false,
    val selectedSound: Sound? = null,
    val isApplying: Boolean = false,
    val applyingContactId: Long? = null,
    val success: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val contactService: ContactRingtoneService,
    private val soundApplier: SoundApplier,
    private val favoritesRepo: FavoritesRepository,
    private val bundledContent: BundledContentProvider,
    private val soundUrlResolver: SoundUrlResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactPickerState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun setPermissionGranted(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) search(_state.value.query, immediate = true)
    }

    suspend fun ensureSelectedSound(soundId: String, fallbackSound: Sound?): Boolean {
        val resolved = resolveSound(soundId)
        val sound = when {
            fallbackSound == null -> resolved
            resolved == null -> fallbackSound
            matchesFallbackIdentity(resolved, fallbackSound) -> resolved
            else -> fallbackSound
        } ?: run {
            _state.update { it.copy(selectedSound = null) }
            return false
        }
        _state.update { it.copy(selectedSound = sound, error = null) }
        return true
    }

    fun search(query: String, immediate: Boolean = false) {
        searchJob?.cancel()
        _state.update { it.copy(query = query, isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                if (!immediate && query.isNotBlank()) {
                    delay(250)
                }
                val contacts = contactService.searchContacts(query)
                _state.update { current ->
                    if (current.query != query) current
                    else current.copy(contacts = contacts, isLoading = false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { current ->
                    if (current.query != query) current
                    else current.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun assignToContact(contactId: Long) {
        val sound = _state.value.selectedSound ?: run {
            _state.update { it.copy(error = "No sound selected. Return to Sounds and choose a valid item.") }
            return
        }
        _state.update { it.copy(isApplying = true, applyingContactId = contactId, error = null, success = null) }
        viewModelScope.launch {
            val dlUrl = soundUrlResolver.resolve(sound)
            if (dlUrl.isNullOrBlank()) {
                _state.update { it.copy(isApplying = false, applyingContactId = null, error = "This sound does not have a downloadable ringtone file.") }
                return@launch
            }
            soundApplier.downloadOnly(dlUrl, sound.name, ContentType.RINGTONE)
                .onSuccess { uri ->
                    contactService.setContactRingtone(contactId, uri)
                        .onSuccess {
                            _state.update { it.copy(isApplying = false, applyingContactId = null, success = "Ringtone set for contact") }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isApplying = false, applyingContactId = null, error = e.message) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, applyingContactId = null, error = "Download failed: ${e.message}") }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(success = null, error = null) }

    private suspend fun resolveSound(soundId: String): Sound? {
        favoritesRepo.getLatestByIdAndType(soundId, "SOUND")
            ?.takeIf { it.type == "SOUND" }
            ?.toSound()
            ?.let { return it }

        return listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { it.id == soundId }
    }

    private fun matchesFallbackIdentity(sound: Sound, fallbackSound: Sound): Boolean {
        if (sound.id != fallbackSound.id) return false
        if (sound.source != fallbackSound.source) return false
        if (fallbackSound.previewUrl.isNotBlank() && sound.previewUrl != fallbackSound.previewUrl) return false
        if (fallbackSound.downloadUrl.isNotBlank() && sound.downloadUrl != fallbackSound.downloadUrl) return false
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    soundId: String,
    fallbackSound: Sound? = null,
    onBack: () -> Unit,
    viewModel: ContactPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    val soundIdentityKey = remember(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl) {
        listOf(
            soundId,
            fallbackSound?.source?.name.orEmpty(),
            fallbackSound?.previewUrl.orEmpty(),
            fallbackSound?.downloadUrl.orEmpty(),
        ).joinToString("|")
    }
    var soundResolved by remember(soundIdentityKey) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(soundIdentityKey) {
        soundResolved = viewModel.ensureSelectedSound(soundId, fallbackSound)
    }

    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        val writeGranted = permissions[Manifest.permission.WRITE_CONTACTS] == true
        if (readGranted && writeGranted) {
            viewModel.setPermissionGranted(true)
        } else {
            viewModel.setPermissionGranted(false)
            val activity = context as? android.app.Activity
            if (activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)
            ) {
                permissionPermanentlyDenied = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasRead = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasRead && hasWrite) {
            viewModel.setPermissionGranted(true)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            )
        }
    }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assign to Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (soundResolved) {
                null -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Opening contact picker...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Scaffold
                }
                false -> {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        AuraStateCard(
                            icon = Icons.Default.MusicOff,
                            title = "Sound unavailable",
                            description = "The selected sound could not be restored. Return to Sounds and choose another item.",
                            tone = MaterialTheme.colorScheme.tertiary,
                            primaryAction = AuraStateAction("Back to sounds", Icons.AutoMirrored.Filled.ArrowBack, onBack),
                        )
                    }
                    return@Scaffold
                }
                true -> Unit
            }

            if (!state.hasPermission) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    if (permissionPermanentlyDenied) {
                        AuraStateCard(
                            icon = Icons.Default.Contacts,
                            title = "Contacts access is off",
                            description = "Enable contacts permission in Android settings so Aura can assign this sound to one person.",
                            tone = MaterialTheme.colorScheme.tertiary,
                            primaryAction = AuraStateAction(
                                label = "Open settings",
                                icon = Icons.Default.Settings,
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                            ),
                        )
                    } else {
                        AuraStateCard(
                            icon = Icons.Default.Contacts,
                            title = "Allow contacts access",
                            description = "Aura needs read and write contacts permission to set a custom ringtone for a selected contact.",
                            tone = MaterialTheme.colorScheme.primary,
                            primaryAction = AuraStateAction(
                                label = "Allow contacts",
                                icon = Icons.Default.Check,
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                    )
                                },
                            ),
                            secondaryAction = AuraStateAction(
                                label = "Back",
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                onClick = onBack,
                            ),
                        )
                    }
                }
                return@Scaffold
            }

            state.selectedSound?.let { sound ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Assign as this contact's ringtone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            CompactSearchField(
                value = state.query,
                onValueChange = { viewModel.search(it) },
                placeholder = "Search contacts",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = Icons.Default.Search,
                onClear = { viewModel.search("", immediate = true); focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            if (state.isLoading) {
                ContactListSkeleton()
            } else if (state.contacts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.PersonSearch,
                        title = if (state.query.isNotBlank()) "No matching contacts" else "No contacts found",
                        description = if (state.query.isNotBlank()) {
                            "Try a different name or clear the search to show all contacts."
                        } else {
                            "Aura could not find contacts on this device after permission was granted."
                        },
                        tone = MaterialTheme.colorScheme.tertiary,
                        primaryAction = if (state.query.isNotBlank()) {
                            AuraStateAction("Clear search", Icons.Default.Close) {
                                viewModel.search("", immediate = true)
                                focusManager.clearFocus()
                            }
                        } else {
                            null
                        },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            enabled = !state.isApplying,
                            isApplying = state.applyingContactId == contact.id,
                            onClick = { viewModel.assignToContact(contact.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactListSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(8) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBox(Modifier.size(44.dp), shape = RoundedCornerShape(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerBox(Modifier.width(150.dp).height(14.dp), shape = RoundedCornerShape(5.dp))
                        ShimmerBox(Modifier.width(96.dp).height(10.dp), shape = RoundedCornerShape(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactInfo,
    enabled: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isApplying) 1f else 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.22f else 0.1f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled || isApplying) 1f else 0.55f)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        contact.name.take(1).uppercase(Locale.ROOT).ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isApplying) {
                        "Assigning ringtone..."
                    } else if (contact.currentRingtoneUri != null) {
                        "Custom ringtone set"
                    } else {
                        "Tap to assign"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (contact.currentRingtoneUri != null || isApplying) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isApplying) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
