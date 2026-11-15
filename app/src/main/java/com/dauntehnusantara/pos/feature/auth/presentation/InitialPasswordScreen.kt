package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.feature.auth.domain.AuthState
import com.dauntehnusantara.pos.feature.staff.domain.StaffCreationRules

@Composable
internal fun InitialPasswordScreen(state:AuthState,change:(String,String)->Unit,signOut:()->Unit) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AccessScaffold {
        AppScreenHeader("Ganti kata sandi sementara",description="Buat kata sandi pribadi sebelum menggunakan POS. Setelah berhasil, masuk kembali.")
        AuthMessageText(state.message)
        AppTextField(current,{if(it.length<=128)current=it},"Kata sandi saat ini",Modifier.fillMaxWidth().testTag("password.current"),enabled=!state.busy,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation())
        AppTextField(password,{if(it.length<=72)password=it},"Kata sandi baru",Modifier.fillMaxWidth().testTag("password.new"),enabled=!state.busy,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation())
        AppTextField(confirm,{if(it.length<=72)confirm=it},"Ulangi kata sandi baru",Modifier.fillMaxWidth().testTag("password.confirm"),enabled=!state.busy,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation())
        Text("12–72 karakter, maksimal 72 byte; harus berbeda dari kata sandi sementara.")
        AppPrimaryButton("Simpan kata sandi",{change(current,password)},Modifier.fillMaxWidth().testTag("password.save"),
            enabled=current.isNotEmpty()&&password!=current&&password==confirm&&StaffCreationRules.password(password),busy=state.busy)
        TextButton(onClick=signOut,enabled=!state.busy){Text("Keluar")}
        Spacer(Modifier.height(8.dp))
    }
}
