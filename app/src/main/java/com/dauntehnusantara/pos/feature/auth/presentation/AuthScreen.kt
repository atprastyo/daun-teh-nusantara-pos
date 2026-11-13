package com.dauntehnusantara.pos.feature.auth.presentation

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticRole
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun AuthRoot(viewModel: AuthViewModel, demo: @Composable () -> Unit) {
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
        if (state.stage == AuthStage.READY) AccessPortal(state, viewModel)
        else LoginScreen(state, viewModel, if (BuildConfig.DEBUG) ({ demoMode = true }) else null)
    }
}

// This credential-intent path preserves API 26 compatibility without an additional UI library.
@Suppress("DEPRECATION")
@Composable
private fun LoginScreen(state: AuthState, model: AuthViewModel, onDemo: (() -> Unit)?) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var activate by rememberSaveable { mutableStateOf(false) }
    var credentialError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val credentialTitle = stringResource(R.string.auth_offline_prompt)
    val credentialDescription = stringResource(R.string.auth_offline_description)
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) model.offline() else credentialError = true
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        AuthMessageText(state.message)
        if (credentialError) Text(stringResource(R.string.auth_credential_failed), color = MaterialTheme.colorScheme.error)
        when (state.stage) {
            AuthStage.LOADING -> CircularProgressIndicator()
            AuthStage.OUTLETS -> {
                Text(stringResource(R.string.auth_select_outlet), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.auth_enroll_notice))
                state.choices.forEach { membership ->
                    OutlinedButton(onClick = { model.select(membership.outletId) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("${membership.brandName} · ${membership.outletName} · ${membership.role.name}")
                    }
                }
                TextButton(onClick = model::signOut, enabled = !state.busy) { Text(stringResource(R.string.auth_sign_out)) }
            }
            AuthStage.LOCKED -> {
                Text(stringResource(R.string.auth_locked))
                state.pendingDeviceId?.let { Text(stringResource(R.string.auth_device_id, it)) }
                Button(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.auth_online_retry)) }
                Button(onClick = {
                    credentialError = false
                    val intent = context.getSystemService(KeyguardManager::class.java)
                        .createConfirmDeviceCredentialIntent(credentialTitle, credentialDescription)
                    if (intent == null) credentialError = true else credential.launch(intent)
                }, enabled = state.canOffline && !state.busy) { Text(stringResource(R.string.auth_offline_unlock)) }
                Text(stringResource(R.string.auth_offline_limits))
                TextButton(onClick = model::signOut, enabled = !state.busy) { Text(stringResource(R.string.auth_sign_out)) }
            }
            else -> {
                val activation = activate || state.stage == AuthStage.ACTIVATE
                Text(stringResource(if (activation) R.string.auth_activate else R.string.auth_sign_in), style = MaterialTheme.typography.titleLarge)
                if (!state.configured && state.message != AuthMessage.CONFIGURATION) Text(stringResource(R.string.auth_configure))
                if (activation) {
                    Text(stringResource(R.string.auth_activation_instructions))
                    if (!state.activationPending) OutlinedTextField(code, { if (it.length <= 512) code = it }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.auth_activation_code)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                } else OutlinedTextField(email, { if (it.length <= 254) email = it }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), label = { Text(stringResource(R.string.auth_email)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { if (it.length <= 128) password = it }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text(stringResource(if (activation) R.string.auth_new_password else R.string.auth_password)) },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (activation) model.activate(code.trim(), password) else model.signIn(email, password); password = "" },
                    enabled = state.configured && !state.busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (activation) R.string.auth_activate else R.string.auth_sign_in))
                }
                if (!state.activationPending) TextButton(onClick = { activate = !activate; password = ""; code = "" }, enabled = !state.busy) {
                    Text(stringResource(if (activation) R.string.auth_back_login else R.string.auth_have_invite))
                }
            }
        }
        if (state.busy) CircularProgressIndicator()
        if (onDemo != null && state.stage == AuthStage.LOGIN) TextButton(onClick = onDemo, enabled = !state.busy) { Text(stringResource(R.string.auth_demo)) }
    }
}

@Composable
private fun AccessPortal(state: AuthState, model: AuthViewModel) {
    val membership = requireNotNull(state.membership)
    val destinations = RoleNavigation.destinations(membership.role)
    var selected by rememberSaveable(membership.outletId, membership.role.name) { mutableStateOf(destinations.first().name) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxWidth < 600.dp
        Scaffold(bottomBar = {
            if (compact) NavigationBar { destinations.forEach { destination ->
                NavigationBarItem(selected = selected == destination.name, onClick = { selected = destination.name },
                    icon = { Icon(Icons.Default.Info, null) }, label = { Text(stringResource(destination.label())) })
            } }
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (!compact) NavigationRail { destinations.forEach { destination ->
                    NavigationRailItem(selected = selected == destination.name, onClick = { selected = destination.name },
                        icon = { Icon(Icons.Default.Info, null) }, label = { Text(stringResource(destination.label())) })
                } }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(membership.brandName, style = MaterialTheme.typography.titleLarge)
                    if (state.email.isNotBlank()) Text(state.email)
                    Text("${membership.outletName} · ${membership.role.name}")
                    Text(stringResource(if (state.offline) R.string.auth_offline_status else R.string.auth_online_status))
                    state.leaseExpiresAtMs?.let { Text(stringResource(R.string.auth_lease_expiry, formatJakarta(it))) }
                    AuthMessageText(state.message)
                    Text(stringResource(AccessDestination.valueOf(selected).label()), style = MaterialTheme.typography.headlineMedium)
                    if (selected == AccessDestination.STAFF.name) InviteForm(state, model)
                    else Text(stringResource(R.string.auth_portal_boundary))
                    if (selected == AccessDestination.SETTINGS.name) {
                        Button(onClick = model::refresh, enabled = !state.busy) { Text(stringResource(R.string.auth_online_retry)) }
                        OutlinedButton(onClick = model::switchOutlet, enabled = !state.offline && !state.busy) { Text(stringResource(R.string.auth_switch_outlet)) }
                        TextButton(onClick = model::signOut, enabled = !state.busy) { Text(stringResource(R.string.auth_sign_out)) }
                        Text(stringResource(R.string.auth_offline_limits))
                    }
                    if (state.busy) CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun InviteForm(state: AuthState, model: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    val membership = requireNotNull(state.membership)
    val actor = membership.role
    var roleName by rememberSaveable(membership.outletId, actor.name) { mutableStateOf(Role.CASHIER.name) }
    OutlinedTextField(email, { if (it.length <= 254) email = it }, enabled = !state.busy && !state.offline,
        label = { Text(stringResource(R.string.auth_email)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    listOf(Role.CASHIER, Role.MANAGER).filter { RoleNavigation.mayInvite(actor, it) }.forEach { role ->
        Row(Modifier.fillMaxWidth().selectable(selected = roleName == role.name, role = SemanticRole.RadioButton, enabled = !state.busy && !state.offline) { roleName = role.name }.padding(vertical = 8.dp)) {
            RadioButton(selected = roleName == role.name, onClick = null); Text(role.name, Modifier.padding(start = 12.dp))
        }
    }
    Button(onClick = { model.invite(email, Role.valueOf(roleName)) }, enabled = !state.busy && !state.offline && '@' in email) { Text(stringResource(R.string.auth_send_invite)) }
    if (state.offline) Text(stringResource(R.string.auth_invite_online))
    OutlinedButton(onClick = model::devices, enabled = !state.offline && !state.busy) { Text(stringResource(R.string.auth_load_devices)) }
    state.deviceRequests.forEach { request ->
        Text("${request.fullName} · ${request.email} · ${request.role.name}")
        Text(request.deviceId)
        Button(onClick = { model.approve(request.deviceId) }, enabled = !state.offline && !state.busy) { Text(stringResource(R.string.auth_approve_device)) }
    }
}

@Composable
private fun AuthMessageText(message: AuthMessage?) {
    if (message == null) return
    Text(stringResource(when (message) {
        AuthMessage.RATE_LIMIT -> R.string.auth_rate_limit
        AuthMessage.INVITE_CONFLICT -> R.string.auth_invite_conflict
        AuthMessage.DEVICE_PENDING -> R.string.auth_device_pending
        AuthMessage.CONFIGURATION -> R.string.auth_configure
        AuthMessage.INVALID_INPUT -> R.string.auth_invalid
        AuthMessage.SIGN_IN_FAILED -> R.string.auth_sign_in_failed
        AuthMessage.UNAVAILABLE -> R.string.auth_unavailable
        AuthMessage.NO_ACCESS -> R.string.auth_no_access
        AuthMessage.DENIED -> R.string.auth_denied
        AuthMessage.OFFLINE_EXPIRED -> R.string.auth_lease_invalid
        AuthMessage.INVITE_SENT -> R.string.auth_invite_sent
        AuthMessage.INVITE_UNCERTAIN -> R.string.auth_invite_uncertain
    }))
}

private fun AccessDestination.label(): Int = when (this) {
    AccessDestination.OVERVIEW -> R.string.auth_overview
    AccessDestination.POS -> R.string.cashier
    AccessDestination.HISTORY -> R.string.history
    AccessDestination.CATALOG -> R.string.catalog_title
    AccessDestination.REPORTS -> R.string.reports
    AccessDestination.STAFF -> R.string.auth_staff
    AccessDestination.SETTINGS -> R.string.settings
}
