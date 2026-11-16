package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.auth.domain.Role

/** Uses window width and font scale, rather than a tablet/device flag. */
@Composable
internal fun AccessScaffold(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val twoPanes = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.5f
        Row(Modifier.fillMaxSize()) {
            if (twoPanes) Surface(Modifier.weight(0.42f).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(40.dp), verticalArrangement = Arrangement.Center) {
                    BrandIdentity()
                    Spacer(Modifier.height(32.dp))
                    Text(stringResource(R.string.access_brand_title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.access_brand_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.weight(if (twoPanes) 0.58f else 1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    BrandIdentity()
                    content()
                }
            }
        }
    }
}

@Composable
internal fun BrandIdentity() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // App appearance is independent of the launcher icon background.
        Image(painterResource(R.drawable.brand_logo), null, Modifier.size(72.dp),
            colorFilter=if(MaterialTheme.colorScheme.surface.luminance()<0.5f)ColorFilter.tint(Color.White)else null)
        Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun AccessHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun AccessStatus(text: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AccessAction(text: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(text)
    }
}

internal fun Role.label(): Int = when (this) {
    Role.OWNER -> R.string.access_owner
    Role.MANAGER -> R.string.access_manager
    Role.CASHIER -> R.string.access_cashier
}
