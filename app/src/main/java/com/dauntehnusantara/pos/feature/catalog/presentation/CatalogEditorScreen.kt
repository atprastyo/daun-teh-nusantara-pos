package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatIdr
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.presentation.ProductPhoto

@Composable
fun CatalogEditorRoute(vm: CatalogEditorViewModel, launchPhotoPicker: (Product) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CatalogEditorScreen(state, vm::save, vm::retrySync, vm::retryCommand, launchPhotoPicker,
        vm::removePhoto, vm::retryPhoto, vm.editor)
}

@Composable
fun CatalogEditorScreen(state: CatalogEditorState, save: (CatalogMutation) -> Unit, refresh: () -> Unit,
    retry: () -> Unit, pickPhoto: (Product) -> Unit = {}, removePhoto: (Product) -> Unit = {},
    retryPhoto: () -> Unit = {}, session: CatalogEditorSession = remember { CatalogEditorSession() }) {
    val ui by session.state.collectAsStateWithLifecycle()
    if (state.loading && state.scope == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.catalog_ui_loading))
        }
        return
    }
    LaunchedEffect(state.scope?.key) { state.scope?.key?.let(session::bindScope) }
    LaunchedEffect(state.completedOperationId, state.waitingThrough, state.pull, state.catalog, ui.submittedOperation) {
        val draft=ui.draft
        if (draft!=null && state.waitingThrough == null && state.pull.initialized && state.pull.error == null &&
            state.catalog.editorRows(draft.original.kind).any { it.id==draft.original.id && it.expectedVersion>draft.original.expectedVersion })
            state.completedOperationId?.let(session::applied)
    }
    LaunchedEffect(state.error) { if (state.error == "CONFLICT") session.conflict() }
    val rows = remember(state.catalog, ui.kind) { state.catalog.editorRows(ui.kind) }
    val visible = remember(rows, ui.query, ui.filter) { rows.filter {
        it.name.contains(ui.query, ignoreCase = true) && when (ui.filter) {
            CatalogFilter.ALL -> true; CatalogFilter.ACTIVE -> it.active; CatalogFilter.ARCHIVED -> !it.active
        }
    } }
    val openNew = {
        if (state.editable && ui.draft == null) {
            val kind = when {
                ui.kind == CatalogRecord.Product && state.catalog.categories.none { it.info.active } -> CatalogRecord.Category
                ui.kind == CatalogRecord.Option && state.catalog.groups.none { it.info.active } -> CatalogRecord.Group
                else -> ui.kind
            }
            session.open(CatalogMutation(kind = kind))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp && LocalDensity.current.fontScale < 1.5f
        val density = LocalDensity.current
        val heightWithoutIme = maxHeight + with(density) { WindowInsets.ime.getBottom(this).toDp() }
        val compactForm = maxWidth < 600.dp || heightWithoutIme < 480.dp || density.fontScale >= 1.5f
        Row(Modifier.fillMaxSize()) {
            if (ui.draft == null || wide || !compactForm) {
                CatalogList(state, ui, visible, refresh, retry, retryPhoto, openNew,
                    edit = { if (state.editable) session.open(it) }, session = session, modifier = Modifier.weight(1f))
            }
            if (ui.draft != null) CatalogEditorHost(state, session, wide, compactForm, save, refresh, pickPhoto, removePhoto, retry, retryPhoto)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogList(state: CatalogEditorState, ui: CatalogEditorUi, rows: List<CatalogMutation>,
    refresh: () -> Unit, retry: () -> Unit, retryPhoto: () -> Unit, add: () -> Unit,
    edit: (CatalogMutation) -> Unit, session: CatalogEditorSession, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth().testTag("catalog.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.catalog_ui_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.catalog_ui_shared), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CatalogStatus(state, retry, retryPhoto)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = add, enabled = state.editable && ui.draft == null) { Text(stringResource(R.string.catalog_ui_add, stringResource(ui.kind.singular()))) }
                    OutlinedButton(onClick = refresh, enabled = !state.busy && !state.photoBusy && !state.offline) { Text(stringResource(R.string.catalog_ui_refresh)) }
                }
                val kinds = listOf(CatalogRecord.Product, CatalogRecord.Category, CatalogRecord.Group, CatalogRecord.Option)
                @Suppress("DEPRECATION")
                ScrollableTabRow(selectedTabIndex = kinds.indexOf(ui.kind), edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surface) {
                    kinds.forEach { kind -> Tab(selected = ui.kind == kind, onClick = { session.tab(kind) }, enabled = ui.draft == null,
                        text = { Text(stringResource(kind.label())) }) }
                }
                OutlinedTextField(ui.query, session::search, label = { Text(stringResource(R.string.catalog_ui_search)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogFilter.entries.forEach { filter -> FilterChip(selected = ui.filter == filter,
                        onClick = { session.filter(filter) }, label = { Text(stringResource(when (filter) {
                            CatalogFilter.ALL -> R.string.catalog_ui_all; CatalogFilter.ACTIVE -> R.string.catalog_ui_active; CatalogFilter.ARCHIVED -> R.string.catalog_ui_archive_filter
                        })) }) }
                }
                Text(stringResource(R.string.catalog_ui_count, rows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!state.loading && rows.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val filtered = ui.query.isNotEmpty() || ui.filter != CatalogFilter.ALL
                Text(if (filtered) stringResource(R.string.catalog_ui_no_results) else stringResource(R.string.catalog_ui_empty, stringResource(ui.kind.singular())))
                if (filtered) TextButton(onClick = session::clearFilters) { Text(stringResource(R.string.catalog_ui_clear_filters)) }
                else if (ui.kind == CatalogRecord.Product) Text(stringResource(R.string.catalog_ui_category_first))
                else if (ui.kind == CatalogRecord.Option) Text(stringResource(R.string.catalog_ui_group_first))
            }
        }
        items(rows, key = { it.id }) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.kind == CatalogRecord.Product) state.catalog.products.find { it.info.id == row.id }?.let {
                    ProductPhoto(it.imagePath, it.info.name, Modifier.size(80.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    when (row.kind) {
                        CatalogRecord.Category -> Text(stringResource(R.string.catalog_ui_order_display, row.sortOrder), style = MaterialTheme.typography.bodyMedium)
                        CatalogRecord.Group -> Text(stringResource(R.string.catalog_ui_bounds, row.minSelect ?: 0, row.maxSelect ?: 1), style = MaterialTheme.typography.bodyMedium)
                        else -> {
                            val parent = if (row.kind == CatalogRecord.Product) state.catalog.categories.find { it.info.id == row.parentId }?.info else state.catalog.groups.find { it.info.id == row.parentId }?.info
                            parent?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(formatIdr(row.priceIdr ?: 0), style = MaterialTheme.typography.titleMedium)
                            if (row.kind == CatalogRecord.Product && row.active && parent?.active == false) Text(stringResource(R.string.catalog_ui_category_archived))
                        }
                    }
                    Text(stringResource(if (row.active) R.string.catalog_ui_active else R.string.catalog_ui_archived), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val editDescription = stringResource(R.string.catalog_ui_edit_title, row.name)
                TextButton(onClick = { edit(row) }, enabled = state.editable && ui.draft == null, modifier = Modifier.semantics { contentDescription = editDescription }) { Text(stringResource(R.string.catalog_ui_edit)) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun CatalogNotice(text: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CatalogStatus(state: CatalogEditorState, retry: () -> Unit = {}, retryPhoto: () -> Unit = {}) {
    if (state.loading || !state.pull.initialized || state.pull.epoch == null || state.busy || state.photoBusy || state.refreshing || state.waitingThrough != null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(when {
            state.loading || !state.pull.initialized || state.pull.epoch == null -> R.string.catalog_ui_loading; state.photoBusy -> R.string.catalog_ui_photo_busy
            state.busy -> R.string.catalog_ui_saving; state.waitingThrough != null -> R.string.catalog_ui_waiting
            else -> R.string.catalog_ui_refreshing
        }), style = MaterialTheme.typography.bodyMedium)
    }
    if (state.offline) CatalogNotice(stringResource(R.string.catalog_ui_offline))
    if (state.saved && !state.refreshing && !state.refreshRequired && !state.busy && !state.photoBusy && state.waitingThrough == null && state.pull.initialized && state.pull.error == null) CatalogNotice(stringResource(R.string.catalog_ui_applied))
    if (state.refreshRequired && state.error != "CONFLICT" && state.photoError != "CONFLICT") CatalogNotice(stringResource(R.string.catalog_ui_conflict), error = true)
    (state.error ?: state.pull.error)?.let { code -> CatalogNotice(stringResource(when (code) {
        "CONFLICT" -> R.string.catalog_ui_conflict; "INVALID" -> R.string.catalog_ui_invalid
        "AUTH_REQUIRED" -> R.string.catalog_ui_auth; "REBOOTSTRAP_REQUIRED" -> R.string.catalog_ui_recovery
        else -> R.string.catalog_ui_unknown
    }), error = true) }
    state.photoError?.let { code -> CatalogNotice(stringResource(when (code) {
        "INVALID_IMAGE" -> R.string.catalog_ui_photo_invalid; "CONFLICT" -> R.string.catalog_ui_photo_conflict
        "INVALID" -> R.string.catalog_ui_photo_setup; "AUTH_REQUIRED" -> R.string.catalog_ui_auth
        "REBOOTSTRAP_REQUIRED" -> R.string.catalog_ui_recovery; else -> R.string.catalog_ui_photo_unknown
    }), error = true) }
    if (state.pending != null && !state.busy && state.error == null) CatalogNotice(stringResource(R.string.catalog_ui_unknown))
    if (state.photoCommand != null && !state.photoBusy && state.photoError == null) CatalogNotice(stringResource(R.string.catalog_ui_photo_unknown))
    if (state.pending != null) OutlinedButton(onClick = retry, enabled = !state.busy && !state.offline && !state.photoBusy) { Text(stringResource(R.string.catalog_ui_retry)) }
    if (state.photoCommand != null) OutlinedButton(onClick = retryPhoto, enabled = !state.photoBusy && !state.offline && !state.busy) { Text(stringResource(R.string.catalog_ui_retry_photo)) }
}

internal fun CatalogRecord.label(): Int = when (this) {
    CatalogRecord.Product -> R.string.catalog_ui_products; CatalogRecord.Category -> R.string.catalog_ui_categories
    CatalogRecord.Group -> R.string.catalog_ui_groups; CatalogRecord.Option -> R.string.catalog_ui_options
}
internal fun CatalogRecord.singular(): Int = when (this) {
    CatalogRecord.Product -> R.string.catalog_ui_product; CatalogRecord.Category -> R.string.catalog_ui_category
    CatalogRecord.Group -> R.string.catalog_ui_group; CatalogRecord.Option -> R.string.catalog_ui_option
}
