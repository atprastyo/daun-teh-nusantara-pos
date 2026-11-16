package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.auth.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRequestsPanel(state: AuthState, load: () -> Unit, approve: (String) -> Unit, interactionEnabled:Boolean=true) {
    val membership = state.membership ?: return
    if (membership.role == Role.CASHIER) return
    var selectedId by remember(membership.outletId, membership.role, state.email) { mutableStateOf<String?>(null) }
    var submitted by remember(membership.outletId, membership.role, state.email) { mutableStateOf(false) }
    var completed by remember(membership.outletId, membership.role, state.email) { mutableStateOf(false) }
    val selected = state.deviceRequests.firstOrNull { it.deviceId == selectedId }
    LaunchedEffect(state.busy, state.offline, state.deviceRequests, state.deviceRequestsLoaded, interactionEnabled) {
        if (submitted && !state.busy) {
            completed = state.deviceRequestsLoaded && state.message == null && state.deviceRequests.none { it.deviceId == selectedId }
            submitted = false
            selectedId = null
        }
        if (state.offline || selected == null || (!interactionEnabled && !submitted)) selectedId = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.access_requests_title), style = MaterialTheme.typography.titleLarge)
        Text(membership.outletName, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.auth_staff_manual), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.access_requests_policy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AuthMessageText(state.message)
        if (completed) AccessStatus(stringResource(R.string.access_approved_result))
        OutlinedButton(onClick = { completed = false; load() }, enabled = interactionEnabled && !state.offline && !state.busy) {
            Text(stringResource(R.string.auth_load_devices))
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!state.busy && !state.offline) {
            if (!state.deviceRequestsLoaded) Text(stringResource(R.string.access_requests_unloaded))
            else if (state.deviceRequests.isEmpty()) AccessStatus(stringResource(R.string.access_requests_empty))
        }
        state.deviceRequests.forEach { request ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(request.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(request.email, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(request.role.label()), color = MaterialTheme.colorScheme.primary)
                    Text(DeviceName.clean(request.deviceName)?:stringResource(R.string.device_name_unknown),Modifier.testTag("device.request.name.${request.deviceId}"),style=MaterialTheme.typography.titleMedium)
                    DeviceIdentity(request.deviceId)
                    TextButton(onClick = { selectedId = request.deviceId; completed = false }, enabled = interactionEnabled && !state.offline && !state.busy && state.deviceRequestsLoaded) {
                        Text(stringResource(R.string.access_review))
                    }
                }
            }
        }
    }
    if (selected != null && !state.offline) {
        val dismiss = { if (!state.busy && !submitted) selectedId = null }
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints {
            val body: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AccessHeading(stringResource(R.string.access_approve_title), stringResource(R.string.access_approve_help))
                    Text(selected.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(selected.email)
                    Text("${membership.outletName} · ${stringResource(selected.role.label())}")
                    Text(DeviceName.clean(selected.deviceName)?:stringResource(R.string.device_name_unknown),Modifier.testTag("device.approval.name"),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.device_name_hint),style=MaterialTheme.typography.bodySmall)
                    DeviceIdentity(selected.deviceId)
                    AccessAction(stringResource(if (submitted) R.string.access_approving else R.string.auth_approve_device),
                        interactionEnabled && !state.busy && !submitted && state.deviceRequestsLoaded, submitted) {
                        submitted = true
                        approve(selected.deviceId)
                    }
                    TextButton(onClick = dismiss, enabled = !state.busy && !submitted) { Text(stringResource(R.string.access_cancel)) }
                }
            }
            if (maxWidth >= 600.dp && fontScale < 1.5f) {
                androidx.compose.ui.window.Dialog(onDismissRequest = dismiss,
                    properties = DialogProperties(dismissOnBackPress = !state.busy && !submitted, dismissOnClickOutside = !state.busy && !submitted)) {
                    Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { body() }
                }
            } else ModalBottomSheet(onDismissRequest = dismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.busy && !submitted }),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.busy && !submitted)) {
                body()
            }
        }
    }
}
