package com.dauntehnusantara.pos.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.feature.settings.domain.ThemeMode

@Composable
internal fun AppearanceSettings(state:AppearanceState,onChange:(ThemeMode)->Unit,onRetry:()->Unit) {
    AppSection(stringResource(R.string.settings_appearance)) {
        Text(stringResource(R.string.settings_theme_device))
        if(state.error!=null) {
            AppErrorText(stringResource(if(state.error==AppearanceError.READ)R.string.settings_theme_read_error else R.string.settings_theme_write_error))
            TextButton(onClick=onRetry,enabled=!state.saving){Text(stringResource(R.string.retry))}
        }
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach{mode->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("settings.theme.${mode.name}")
                    .selectable(state.mode==mode,enabled=!state.saving&&state.mode!=null,role=Role.RadioButton,onClick={onChange(mode)})
                    .padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    RadioButton(state.mode==mode,onClick=null,enabled=!state.saving&&state.mode!=null)
                    Text(stringResource(if(mode==ThemeMode.LIGHT)R.string.settings_theme_light else R.string.settings_theme_dark))
                }
            }
        }
        if(state.saving)LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
