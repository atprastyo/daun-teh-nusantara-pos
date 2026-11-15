package com.dauntehnusantara.pos.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class NoticeTone { NEUTRAL, ERROR, SUCCESS }
@Composable
fun AppNotice(message:String,modifier:Modifier=Modifier,tone:NoticeTone=NoticeTone.NEUTRAL) {
    val colors=MaterialTheme.colorScheme
    val background=when(tone){NoticeTone.NEUTRAL->colors.surfaceContainerHighest;NoticeTone.ERROR->colors.errorContainer;NoticeTone.SUCCESS->colors.primaryContainer}
    val foreground=when(tone){NoticeTone.NEUTRAL->colors.onSurface;NoticeTone.ERROR->colors.onErrorContainer;NoticeTone.SUCCESS->colors.onPrimaryContainer}
    Surface(modifier.fillMaxWidth(),color=background,contentColor=foreground,shape=MaterialTheme.shapes.medium) {
        Text(message,Modifier.padding(16.dp).semantics{liveRegion=LiveRegionMode.Polite})
    }
}
@Composable fun AppErrorText(message:String,modifier:Modifier=Modifier){Text(message,modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)}
@Composable
fun AppScreenHeader(title:String,modifier:Modifier=Modifier,description:String?=null){Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text(title,Modifier.semantics{heading()},style=MaterialTheme.typography.headlineMedium)
    description?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
}}
@Composable
fun AppSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text(title,Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge);content();HorizontalDivider(Modifier.padding(top=8.dp))
}}
@Composable
fun AppListRow(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){content();HorizontalDivider(Modifier.padding(top=8.dp))}}
@Composable
fun AppEmptyState(message:String,modifier:Modifier=Modifier){Text(message,modifier,style=MaterialTheme.typography.bodyLarge)}
/** Public identities only. Callers must not put credentials or secret URLs here. */
@Composable
fun AppIdentity(value:String,modifier:Modifier=Modifier,label:String?=null){Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
    label?.let{Text(it,style=MaterialTheme.typography.labelMedium)}
    SelectionContainer{Text(value,style=MaterialTheme.typography.bodySmall)}
}}
@Composable
fun AppPrimaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,busy:Boolean=false){
    Button(onClick,modifier.heightIn(min=48.dp),enabled=enabled&&!busy){
        if(busy){CircularProgressIndicator(Modifier.size(18.dp),color=LocalContentColor.current,strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
        Text(text)
    }
}

/** Presentation only: feature callers own identity, permission, validation and effects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfirmation(title:String,onDismiss:()->Unit,busy:Boolean=false,
    actions:@Composable ColumnScope.()->Unit,content:@Composable ColumnScope.()->Unit) {
    val dismiss={if(!busy)onDismiss()}
    val body:@Composable ()->Unit={
        Column(Modifier.fillMaxWidth().heightIn(max=640.dp).imePadding().padding(horizontal=20.dp)){
            Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(title,Modifier.padding(top=20.dp).semantics{heading()},style=MaterialTheme.typography.headlineSmall)
                content()
            }
            Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=actions)
        }
    }
    BoxWithConstraints {
        if(maxWidth>=600.dp&&maxHeight>=500.dp&&LocalDensity.current.fontScale<1.5f)
            Dialog(dismiss,properties=DialogProperties(dismissOnBackPress=!busy,dismissOnClickOutside=!busy)){
                Surface(Modifier.widthIn(max=560.dp).fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge){body()}
            }
        else ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={!busy}),properties=ModalBottomSheetProperties(shouldDismissOnBackPress=!busy)){body()}
    }
}
