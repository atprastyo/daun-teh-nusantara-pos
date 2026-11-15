package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatJakarta
import com.dauntehnusantara.pos.feature.auth.domain.*

/** Callback-only presentation: role, approval and offline authority remain in the auth layer. */
@Composable
fun AccessScreen(state: AuthState, signIn: (String, String) -> Unit, select: (String) -> Unit,
    refresh: () -> Unit, signOut: () -> Unit, onDemo: (() -> Unit)? = null,
    credentialError: Boolean = false, credentialOpen: Boolean = false, unlock: () -> Unit = {}) {
    var selectedOutlet by remember { mutableStateOf<String?>(null) }
    var checkedApproval by remember(state.pendingDeviceId) { mutableStateOf(false) }
    LaunchedEffect(state.stage, state.busy) {
        if (!state.busy) selectedOutlet = null
    }
    AccessScaffold {
        when (state.stage) {
            AuthStage.LOADING -> {
                AccessHeading(stringResource(R.string.access_restoring), stringResource(R.string.access_restoring_body))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AuthStage.PASSWORD_CHANGE -> Unit // Dedicated memory-only password screen in AuthRoot.
            AuthStage.LOGIN -> LoginForm(state, signIn, onDemo)
            AuthStage.OUTLETS -> {
                AccessHeading(stringResource(R.string.auth_select_outlet), stringResource(R.string.access_outlet_help))
                AuthMessageText(state.message)
                if (state.busy) {
                    AccessStatus(stringResource(R.string.access_checking_device) + (selectedOutlet?.let { "\n$it" } ?: ""))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.choices.forEach { membership ->
                    OutlinedCard(onClick = { selectedOutlet = membership.outletName; select(membership.outletId) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_access_store), null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(membership.outletName, style = MaterialTheme.typography.titleMedium)
                                Text(membership.brandName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(membership.role.label()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
                TextButton(onClick = signOut, enabled = !state.busy) { Text(stringResource(R.string.auth_sign_out)) }
            }
            AuthStage.LOCKED -> {
                val pending = state.message == AuthMessage.DEVICE_PENDING || state.busy && state.pendingDeviceId != null
                val heading = when {
                    pending -> R.string.access_pending_title
                    state.canOffline -> R.string.auth_offline_prompt
                    state.message == AuthMessage.NO_ACCESS -> R.string.access_no_outlets
                    state.message == AuthMessage.UNAVAILABLE -> R.string.access_unavailable_title
                    else -> R.string.access_validate_title
                }
                Icon(painterResource(R.drawable.ic_access_shield), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                AccessHeading(stringResource(heading), stringResource(if (pending) R.string.access_pending_help else R.string.access_locked_help))
                state.lockedContext?.let { Text("${it.outletName} · ${stringResource(it.role.label())}", style = MaterialTheme.typography.titleMedium) }
                if (!pending) AuthMessageText(state.message)
                if (credentialError) AccessStatus(stringResource(R.string.auth_credential_failed), error = true)
                if (pending) state.pendingDeviceId?.let { DeviceIdentity(it) }
                if (pending && checkedApproval && !state.busy) AccessStatus(stringResource(R.string.access_still_pending))
                if (state.canOffline) {
                    state.leaseExpiresAtMs?.let { AccessStatus(stringResource(R.string.auth_lease_expiry, formatJakarta(it))) }
                    AccessAction(stringResource(R.string.access_confirm_lock), !state.busy && !credentialOpen, state.busy, unlock)
                    Text(stringResource(R.string.access_offline_bound), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.canOffline) OutlinedButton(onClick = refresh, enabled = !state.busy && !credentialOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.auth_online_retry))
                } else AccessAction(stringResource(if (pending) R.string.access_check_approval else R.string.auth_online_retry), !state.busy && !credentialOpen, state.busy) { if (pending) checkedApproval = true; refresh() }
                if (pending) Text(stringResource(if (state.lockedContext?.role == Role.MANAGER) R.string.access_pending_owner else R.string.access_pending_reviewer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = signOut, enabled = !state.busy && !credentialOpen) { Text(stringResource(R.string.auth_sign_out)) }
            }
            AuthStage.READY -> Unit // AuthRoot owns the scoped shell and initial-pull gate.
        }
    }
}

@Composable
private fun LoginForm(state: AuthState, signIn: (String, String) -> Unit, onDemo: (() -> Unit)?) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password and visibility intentionally do not survive recreation/navigation.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var invalidEmail by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val canSubmit = state.configured && !state.busy && email.isNotBlank() && password.isNotEmpty()
    val submit = {
        if (canSubmit && '@' !in email) {
            invalidEmail = true
        } else if (canSubmit) {
            val submitted = password
            password = ""; visible = false; focus.clearFocus()
            signIn(email.trim(), submitted)
        }
    }
    AccessHeading(stringResource(R.string.auth_sign_in), stringResource(R.string.access_login_help))
    if (!state.configured) AccessStatus(stringResource(R.string.auth_configure))
    if (state.message != AuthMessage.CONFIGURATION) AuthMessageText(state.message)
    OutlinedTextField(email, { if (it.length <= 254) { email = it; invalidEmail = false } }, enabled = !state.busy,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        isError = invalidEmail,
        supportingText = if (invalidEmail) ({ Text(stringResource(R.string.access_email_invalid)) }) else null,
        label = { Text(stringResource(R.string.auth_email)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
    OutlinedTextField(password, { if (it.length <= 128) password = it }, enabled = !state.busy,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }), label = { Text(stringResource(R.string.auth_password)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = !state.busy) {
                Icon(painterResource(if (visible) R.drawable.ic_access_eye_off else R.drawable.ic_access_eye),
                    stringResource(if (visible) R.string.access_hide_password else R.string.access_show_password))
            }
        }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
    AccessAction(stringResource(if (state.busy) R.string.access_checking_account else R.string.auth_sign_in), canSubmit, state.busy, submit)
    Text(stringResource(R.string.access_password_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onDemo != null) {
        HorizontalDivider()
        TextButton(onClick = onDemo, enabled = !state.busy) { Text(stringResource(R.string.auth_demo)) }
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun DeviceIdentity(id: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.access_device_label), style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(id, style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { clipboard.setText(AnnotatedString(id)); copied = true }) {
                Text(stringResource(if (copied) R.string.access_id_copied else R.string.access_copy_id))
            }
        }
    }
}
