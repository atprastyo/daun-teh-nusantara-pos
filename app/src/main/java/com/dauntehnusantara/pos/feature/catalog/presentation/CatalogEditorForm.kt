package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.presentation.ProductPhoto

private data class CatalogConfirmation(val command: CatalogMutation? = null, val photo: Product? = null, val storedOnly: Boolean = false)

@Composable
internal fun CatalogEditorHost(state: CatalogEditorState, session: CatalogEditorSession, wide: Boolean, compact: Boolean,
    save: (CatalogMutation) -> Unit, refresh: () -> Unit, pickPhoto: (Product) -> Unit, removePhoto: (Product) -> Unit,
    retry: () -> Unit, retryPhoto: () -> Unit) {
    val ui by session.state.collectAsStateWithLifecycle()
    val draft = ui.draft ?: return
    var confirmation by remember(draft.original.id, state.scope?.key) { mutableStateOf<CatalogConfirmation?>(null) }
    val latest = remember(state.catalog, draft.original.kind) { state.catalog.editorRows(draft.original.kind).find { it.id == draft.original.id } }
    val stale = draft.original.expectedVersion > 0 && (latest == null || latest.expectedVersion != draft.original.expectedVersion)
    val needsReview = ui.needsReview || stale
    val awaitingVisibleRecord = ui.submittedOperation != null && ui.submittedOperation == state.completedOperationId &&
        (latest == null || latest.expectedVersion <= draft.original.expectedVersion)
    val canChange = state.editable && !needsReview && !awaitingVisibleRecord
    val busy = state.busy || state.photoBusy
    fun submit(command: CatalogMutation) {
        if (!state.editable || needsReview) return
        session.submitted(command.operationId)
        save(command)
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().imePadding().padding(20.dp).testTag("catalog.editor")) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(session.formScroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (draft.original.expectedVersion == 0L) R.string.catalog_ui_new_title else R.string.catalog_ui_edit_title,
                    stringResource(draft.original.kind.singular())), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.catalog_ui_shared), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CatalogStatus(state, retry, retryPhoto)
                if (state.refreshRequired || state.refreshing || state.error != null || state.photoError == "CONFLICT")
                    OutlinedButton(onClick = refresh, enabled = !state.offline && !busy) { Text(stringResource(R.string.catalog_ui_refresh)) }
                if (needsReview) {
                    CatalogNotice(stringResource(R.string.catalog_ui_review))
                    if (latest == null) Text(stringResource(R.string.catalog_ui_form_unavailable))
                    else OutlinedButton(onClick = { session.open(latest) }, enabled = state.editable) { Text(stringResource(R.string.catalog_ui_latest)) }
                }
                CatalogDraftFields(draft, state.catalog, canChange) { session.update(it) }
                if (draft.original.kind == CatalogRecord.Product) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.catalog_ui_photo), style = MaterialTheme.typography.titleMedium)
                            val product = state.catalog.products.find { it.info.id == draft.original.id }
                            if (draft.original.expectedVersion == 0L || product == null) Text(stringResource(R.string.catalog_ui_photo_first))
                            else {
                                ProductPhoto(product.imagePath, product.info.name, Modifier.size(144.dp))
                                Text(stringResource(R.string.catalog_ui_photo_help), style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { pickPhoto(product) }, enabled = canChange) { Text(stringResource(if (product.imagePath == null) R.string.catalog_ui_photo_add else R.string.catalog_ui_photo_replace)) }
                                if (product.imagePath != null) TextButton(onClick = { confirmation = CatalogConfirmation(photo = product) }, enabled = canChange) {
                                    Text(stringResource(R.string.catalog_ui_photo_remove), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (draft.original.expectedVersion > 0 && draft.original.active) {
                    val archiveDraft = CatalogDraft(draft.original).copy(active = false)
                    val archive = archiveDraft.command(state.catalog)
                    TextButton(onClick = { archive?.let { confirmation = CatalogConfirmation(command = it, storedOnly = true) } }, enabled = canChange && archive != null) {
                        Text(stringResource(R.string.catalog_ui_archive, stringResource(draft.original.kind.singular())), color = MaterialTheme.colorScheme.error)
                    }
                    if (archive == null) archiveDraft.errors(state.catalog).values.distinct().forEach { message ->
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    draft.command(state.catalog)?.let { command ->
                        if (draft.original.active && draft.original.expectedVersion > 0 && !draft.active) confirmation = CatalogConfirmation(command = command)
                        else submit(command)
                    }
                }, enabled = canChange && draft.command(state.catalog) != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.catalog_ui_save)) }
                TextButton(onClick = session::close, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.catalog_ui_cancel)) }
            }
        }
    }
    if (wide) Surface(Modifier.width(480.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerHighest) { body() }
    else if (compact) Surface(Modifier.fillMaxSize()) { body() }
    else Dialog(onDismissRequest = { if (!busy) session.close() }, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy, usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.94f), shape = MaterialTheme.shapes.extraLarge) { body() }
    }
    confirmation?.let { target ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null }, properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
            title = { Text(stringResource(if (target.photo != null) R.string.catalog_ui_photo_remove_title else R.string.catalog_ui_archive_title,
                stringResource(draft.original.kind.singular()))) },
            text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (target.photo != null) Text(stringResource(R.string.catalog_ui_photo_remove_help, target.photo.info.name))
                else {
                    Text(stringResource(R.string.catalog_ui_archive_help, requireNotNull(target.command).name))
                    Text(stringResource(if (target.storedOnly) R.string.catalog_ui_archive_stored else R.string.catalog_ui_archive_edits))
                    if (draft.original.kind == CatalogRecord.Category) Text(stringResource(R.string.catalog_ui_archive_category))
                }
            } },
            confirmButton = { TextButton(onClick = {
                if (canChange) { target.photo?.let(removePhoto) ?: target.command?.let(::submit); confirmation = null }
            }, enabled = canChange) { Text(stringResource(when {
                target.photo != null -> R.string.catalog_ui_photo_remove
                target.storedOnly -> R.string.catalog_ui_archive_confirm
                else -> R.string.catalog_ui_archive_save
            })) } },
            dismissButton = { TextButton(onClick = { confirmation = null }, enabled = !busy) { Text(stringResource(R.string.catalog_ui_cancel)) } })
    }
}

@Composable
private fun CatalogDraftFields(draft: CatalogDraft, catalog: Catalog, enabled: Boolean, update: ((CatalogDraft) -> CatalogDraft) -> Unit) {
    val errors = draft.errors(catalog)
    CatalogInput("name", R.string.catalog_ui_name, draft.name, enabled, errors) { value -> if (value.length <= 120) update { it.copy(name = value) } }
    when (draft.original.kind) {
        CatalogRecord.Category -> CatalogInput("order", R.string.catalog_ui_order, draft.order, enabled, errors, numeric = true) { value -> update { it.copy(order = value) } }
        CatalogRecord.Group -> {
            CatalogInput("minimum", R.string.catalog_ui_min, draft.minimum, enabled, errors, numeric = true) { value -> update { it.copy(minimum = value) } }
            CatalogInput("maximum", R.string.catalog_ui_max, draft.maximum, enabled, errors, numeric = true) { value -> update { it.copy(maximum = value) } }
            val min = draft.minimum.toIntOrNull()
            val max = draft.maximum.toIntOrNull()
            if (min != null && max != null && min >= 0 && max > 0 && max >= min)
                Text(if (min == 0) stringResource(R.string.catalog_ui_optional_bounds, max) else stringResource(R.string.catalog_ui_required_bounds, min, max), style = MaterialTheme.typography.bodyMedium)
        }
        CatalogRecord.Product, CatalogRecord.Option -> {
            MoneyInputField(draft.price,{value->if(value.length<=256)update{it.copy(price=value)}},
                stringResource(if(draft.original.kind==CatalogRecord.Product)R.string.catalog_ui_price else R.string.catalog_ui_price_delta),
                Modifier.fillMaxWidth().testTag("catalog.field.price"),enabled=enabled,isError="price" in errors,
                supportingText=errors["price"]?.let{stringResource(it)})
            Text(stringResource(R.string.catalog_ui_money_help), style = MaterialTheme.typography.bodyMedium)
            val option = draft.original.kind == CatalogRecord.Option
            Text(stringResource(if (option) R.string.catalog_ui_groups else R.string.catalog_ui_categories), style = MaterialTheme.typography.titleMedium)
            val parents = if (option) catalog.groups.map { it.info } else catalog.categories.map { it.info }
            if (option && draft.original.expectedVersion > 0) {
                val parent = parents.find { it.id == draft.parent }
                Text(parent?.name.orEmpty() + if (parent?.active == false) " · " + stringResource(R.string.catalog_ui_archived) else "")
                Text(stringResource(R.string.catalog_ui_parent_locked), style = MaterialTheme.typography.bodyMedium)
            } else parents.filter { it.active || it.id == draft.parent }.forEach { parent ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(draft.parent == parent.id, enabled = enabled,
                    role = Role.RadioButton, onClick = { update { it.copy(parent = parent.id) } }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = draft.parent == parent.id, onClick = null, enabled = enabled)
                    Text(parent.name + if (!parent.active) " · " + stringResource(R.string.catalog_ui_archived) else "", Modifier.weight(1f))
                }
            }
            errors["parent"]?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (!option) {
                Text(stringResource(R.string.catalog_ui_optional_groups), style = MaterialTheme.typography.titleMedium)
                if (catalog.groups.isEmpty()) Text(stringResource(R.string.catalog_ui_no_groups))
                catalog.groups.filter { it.info.active || it.info.id in draft.original.groupIds }.forEach { group ->
                    val checked = group.info.id in draft.groups
                    val canToggle = enabled && (group.info.active || checked)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = canToggle, role = Role.Checkbox,
                        onValueChange = { value -> update { it.copy(groups = if (value) it.groups + group.info.id else it.groups - group.info.id) } }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = null, enabled = canToggle)
                        Column(Modifier.weight(1f)) {
                            Text(group.info.name)
                            Text(stringResource(R.string.catalog_ui_bounds, group.minSelect, group.maxSelect), style = MaterialTheme.typography.bodyMedium)
                            if (!group.info.active) Text(stringResource(R.string.catalog_ui_archived))
                        }
                    }
                }
                errors["groups"]?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(draft.active, enabled = enabled, role = Role.Checkbox,
        onValueChange = { value -> update { it.copy(active = value) } }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(draft.active, onCheckedChange = null, enabled = enabled)
        Text(stringResource(R.string.catalog_ui_active_use), Modifier.weight(1f))
    }
    errors["active"]?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun CatalogInput(key: String, label: Int, value: String, enabled: Boolean, errors: Map<String, Int>, numeric: Boolean = false, change: (String) -> Unit) {
    AppTextField(value,{if(it.length<=256)change(it)},stringResource(label),Modifier.fillMaxWidth().testTag("catalog.field.$key"),
        enabled=enabled,isError=key in errors,supportingText=errors[key]?.let{stringResource(it)},
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text))
}
