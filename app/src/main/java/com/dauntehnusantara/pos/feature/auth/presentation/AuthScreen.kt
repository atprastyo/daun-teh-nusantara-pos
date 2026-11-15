package com.dauntehnusantara.pos.feature.auth.presentation

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatJakarta
import com.dauntehnusantara.pos.feature.auth.domain.*

@Composable
fun AuthRoot(viewModel: AuthViewModel, authenticated: (@Composable (AccessDestination, Boolean) -> Unit)? = null, recovery: (@Composable () -> Unit)? = null, settings: (@Composable () -> Unit)? = null, demo: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var demoMode by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) viewModel.foreground() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (demoMode && BuildConfig.DEBUG) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TextButton(onClick = { demoMode = false }) { Text(stringResource(R.string.auth_exit_demo)) }
            Box(Modifier.weight(1f)) { demo() }
        }
        return
    }
    Surface(Modifier.fillMaxSize()) {
        if (state.stage == AuthStage.READY) AccessPortal(state, viewModel, authenticated, recovery, settings)
        else LoginScreen(state, viewModel, if (BuildConfig.DEBUG) ({ demoMode = true }) else null)
    }
}

// Android collects the device credential. No app PIN or credential persistence.
@Suppress("DEPRECATION")
@Composable
private fun LoginScreen(state: AuthState, model: AuthViewModel, onDemo: (() -> Unit)?) {
    var credentialError by remember(state.stage) { mutableStateOf(false) }
    var credentialOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val title = stringResource(R.string.auth_offline_prompt)
    val description = stringResource(R.string.auth_offline_description)
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        credentialOpen = false
        if (it.resultCode == Activity.RESULT_OK) model.offline() else credentialError = true
    }
    AccessScreen(state, model::signIn, model::select, model::refresh, model::signOut, onDemo,
        credentialError, credentialOpen) {
        credentialError = false
        val intent = context.getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) credentialError = true else { credentialOpen = true; credential.launch(intent) }
    }
}

@Composable
private fun AccessPortal(state: AuthState, model: AuthViewModel, authenticated: (@Composable (AccessDestination, Boolean) -> Unit)?, recovery: (@Composable () -> Unit)?, settings: (@Composable () -> Unit)?) {
    AuthenticatedShell(state, model::switchOutlet) { destination, expanded ->
        if(destination==AccessDestination.SETTINGS&&settings!=null)settings()
        else if (authenticated != null && destination != AccessDestination.SETTINGS) {
            authenticated(destination, expanded)
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(destination.shellLabel()), style = MaterialTheme.typography.headlineMedium)
            if (destination == AccessDestination.STAFF) StaffDevicesPanel(state, model)
            else if (destination == AccessDestination.SETTINGS) {
                if (state.email.isNotBlank()) Text(state.email, style = MaterialTheme.typography.bodyMedium)
                state.leaseExpiresAtMs?.let { Text(stringResource(R.string.auth_lease_expiry, formatJakarta(it))) }
                com.dauntehnusantara.pos.feature.sync.presentation.SyncStatusPanel(requireNotNull(state.membership).outletId)
                recovery?.invoke()
                Button(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.auth_online_retry)) }
                TextButton(onClick = model::signOut, enabled = !state.busy) { Text(stringResource(R.string.auth_sign_out)) }
                Text(stringResource(R.string.auth_offline_limits), style = MaterialTheme.typography.bodyMedium)
            } else Text(stringResource(R.string.auth_portal_boundary))
        }
    }
}

@Composable
fun StaffDevicesPanel(state: AuthState, model: AuthViewModel, enabled:Boolean=true) {
    DeviceRequestsPanel(state, model::devices, model::approve, enabled)
}

@Composable
internal fun AuthMessageText(message: AuthMessage?) {
    if (message == null) return
    AccessStatus(stringResource(when (message) {
        AuthMessage.RATE_LIMIT -> R.string.auth_rate_limit
        AuthMessage.DEVICE_PENDING -> R.string.auth_device_pending
        AuthMessage.CONFIGURATION -> R.string.auth_configure
        AuthMessage.INVALID_INPUT -> R.string.auth_invalid
        AuthMessage.SIGN_IN_FAILED -> R.string.auth_sign_in_failed
        AuthMessage.UNAVAILABLE -> R.string.auth_unavailable
        AuthMessage.NO_ACCESS -> R.string.auth_no_access
        AuthMessage.DENIED -> R.string.auth_denied
        AuthMessage.OFFLINE_EXPIRED -> R.string.auth_lease_invalid
    }), error = message in setOf(AuthMessage.INVALID_INPUT, AuthMessage.SIGN_IN_FAILED, AuthMessage.DENIED, AuthMessage.OFFLINE_EXPIRED))
}
