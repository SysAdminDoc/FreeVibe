package com.freevibe.ui.screens.sounds

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
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
import javax.inject.Inject

data class ContactPickerState(
    val contacts: List<ContactInfo> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
    val hasPermission: Boolean = false,
    val selectedSound: Sound? = null,
    val isApplying: Boolean = false,
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
                _state.update { current ->
                    if (current.query != query) current
                    else current.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun assignToContact(contactId: Long) {
        val sound = _state.value.selectedSound ?: run {
            _state.update { it.copy(error = "No sound selected") }
            return
        }
        _state.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            val dlUrl = soundUrlResolver.resolve(sound)
            if (dlUrl.isNullOrBlank()) {
                _state.update { it.copy(isApplying = false, error = "No download URL available for this sound") }
                return@launch
            }
            soundApplier.downloadOnly(dlUrl, sound.name, ContentType.RINGTONE)
                .onSuccess { uri ->
                    contactService.setContactRingtone(contactId, uri)
                        .onSuccess {
                            _state.update { it.copy(isApplying = false, success = "Ringtone set for contact") }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isApplying = false, error = e.message) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = "Download failed: ${e.message}") }
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
    val state by viewModel.state.collectAsState()
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading selected sound...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    return@Scaffold
                }
                false -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicOff,
                                null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Selected sound is no longer available", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = onBack) { Text("Back") }
                        }
                    }
                    return@Scaffold
                }
                true -> Unit
            }

            if (!state.hasPermission) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Contacts,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        if (permissionPermanentlyDenied) {
                            Text("Permission permanently denied", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("Please enable contacts permission in app settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Open Settings")
                            }
                        } else {
                            Text("Contacts permission required", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                )
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
                return@Scaffold
            }

            // Search bar
            state.selectedSound?.let { sound ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "This sound will be assigned as the contact ringtone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonSearch,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (state.query.isNotEmpty()) "No contacts found" else "No contacts on device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isApplying = state.isApplying,
                            onClick = { viewModel.assignToContact(contact.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactInfo,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isApplying,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        contact.name.firstOrNull()?.uppercase() ?: "?",
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
                if (contact.currentRingtoneUri != null) {
                    Text(
                        "Custom ringtone set",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
