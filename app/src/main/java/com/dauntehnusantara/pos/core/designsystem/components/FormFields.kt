package com.dauntehnusantara.pos.core.designsystem.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*

/** Whole IDR only. Invalid input stays visible for feature validation, never repriced. */
fun normalizeMoneyInput(value:String):String {
    val trimmed=value.trim()
    if(trimmed.isEmpty())return ""
    val number=trimmed.replaceFirst(Regex("^Rp[ \\u00A0]*",RegexOption.IGNORE_CASE),"")
    return when {
        number.matches(Regex("[0-9]+"))->number
        number.matches(Regex("[0-9]{1,3}(\\.[0-9]{3})+"))->number.replace(".","")
        else->value
    }
}
fun moneyInputLongOrNull(raw:String):Long?=raw.takeIf{it.isNotEmpty()&&it.all{c->c in '0'..'9'}}?.toLongOrNull()

/** Formatting uses string positions, including overflow drafts; no floating point. */
object IdrInputTransformation:VisualTransformation {
    override fun filter(text:AnnotatedString):TransformedText {
        val raw=text.text
        if(raw.isEmpty()||raw.any{it !in '0'..'9'})return TransformedText(text,OffsetMapping.Identity)
        val positions=IntArray(raw.length+1)
        val grouped=buildString {
            raw.forEachIndexed { index,c ->
                if(index>0&&(raw.length-index)%3==0)append('.')
                positions[index]=length
                append(c)
            }
            positions[raw.length]=length
        }
        return TransformedText(AnnotatedString(grouped),object:OffsetMapping {
            override fun originalToTransformed(offset:Int)=positions[offset.coerceIn(0,raw.length)]
            override fun transformedToOriginal(offset:Int)=grouped.take(offset.coerceIn(0,grouped.length)).count{it!='.'}
        })
    }
}

@Composable
fun AppTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,
    enabled:Boolean=true,isError:Boolean=false,supportingText:String?=null,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,visualTransformation:VisualTransformation=VisualTransformation.None,
    prefix:(@Composable ()->Unit)?=null,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,
    keyboardActions:KeyboardActions=KeyboardActions.Default,singleLine:Boolean=true,
    minLines:Int=1,maxLines:Int=if(singleLine)1 else Int.MAX_VALUE) {
    OutlinedTextField(value,onValueChange,modifier.heightIn(min=56.dp),label={Text(label)},enabled=enabled,isError=isError,
        supportingText=supportingText?.let{{Text(it)}},singleLine=singleLine,minLines=minLines,maxLines=maxLines,keyboardOptions=keyboardOptions,
        keyboardActions=keyboardActions,textStyle=MaterialTheme.typography.bodyLarge,leadingIcon=leadingIcon,trailingIcon=trailingIcon,
        visualTransformation=visualTransformation,prefix=prefix,shape=MaterialTheme.shapes.medium)
}
@Composable
fun MoneyInputField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,
    enabled:Boolean=true,isError:Boolean=false,supportingText:String?=null) {
    AppTextField(value,{onValueChange(normalizeMoneyInput(it))},label,modifier,enabled,isError,supportingText,
        KeyboardOptions(keyboardType=KeyboardType.Number),IdrInputTransformation,prefix={Text("Rp")})
}
