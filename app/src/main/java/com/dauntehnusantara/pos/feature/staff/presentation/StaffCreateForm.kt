package com.dauntehnusantara.pos.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope

/** Deliberately not saveable: password/draft is lost on process or scope loss. */
@Composable
internal fun StaffCreateForm(scope:PullScope,outlet:String,busy:Boolean,error:String?,frozen:StaffCreation?,submit:(StaffCreation)->Unit,cancel:()->Unit) {
    var name by remember(scope.key){mutableStateOf(frozen?.name.orEmpty())}
    var email by remember(scope.key){mutableStateOf(frozen?.email.orEmpty())}
    var password by remember(scope.key){mutableStateOf(frozen?.password.orEmpty())}
    var confirm by remember(scope.key){mutableStateOf(frozen?.password.orEmpty())}
    var role by remember(scope.key){mutableStateOf(frozen?.role?:"CASHIER")}
    val candidate=StaffCreation(name=name,email=email,role=role,password=password)
    val correctingPassword=frozen!=null&&error=="INVALID"
    val submission=if(correctingPassword)StaffCreation(frozen!!.operationId,frozen.name,frozen.email,frozen.role,password) else frozen?:candidate
    val valid=password==confirm&&runCatching{submission.validate(scope)}.isSuccess
    AppConfirmation("Tambah staf",cancel,busy=busy,actions={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=cancel,enabled=!busy){Text("Tutup")}
            AppPrimaryButton(if(frozen!=null)"Periksa / coba perintah sama" else "Buat akun",{submit(submission)},Modifier.weight(1f).testTag("staff.create.submit"),enabled=if(correctingPassword)valid else frozen!=null||valid,busy=busy)
        }
    }) {
        Text("Outlet: $outlet",style=MaterialTheme.typography.titleMedium)
        Text("Akun baru hanya untuk outlet ini. Pemilik dapat memilih outlet lain dari navigasi sebelum membuat akun.")
        if(error!=null)AppNotice(when(error){
            "NOT_CONFIGURED"->"Fitur server belum dipasang. Hubungi administrator."
            "CONFLICT"->"Email atau perintah sudah digunakan. Akun yang ada tidak diubah; tinjau administrator."
            "RATE_LIMIT"->"Batas pembuatan akun tercapai. Coba lagi nanti."
            "INVALID"->"Kata sandi ditolak. Perbaiki sesuai kebijakan server lalu coba perintah yang sama."
            else->"Hasil belum pasti. Periksa / coba perintah yang sama; jangan buat akun pengganti."
        },tone=NoticeTone.ERROR)
        if(frozen!=null)Text("Detail dikunci untuk pemeriksaan ulang. Kata sandi sementara yang pertama dipakai tidak diubah oleh pengulangan.")
        val editable=!busy&&frozen==null
        val passwordEditable=!busy&&(frozen==null||correctingPassword)
        AppTextField(name,{if(it.length<=120)name=it},"Nama lengkap",Modifier.fillMaxWidth().testTag("staff.create.name"),enabled=editable)
        AppTextField(email,{if(it.length<=254)email=it},"Email",Modifier.fillMaxWidth().testTag("staff.create.email"),enabled=editable,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            StaffCreationRules.roles(scope.role).forEach { option->
                FilterChip(role==option,{role=option},label={Text(staffRole(option))},enabled=editable,modifier=Modifier.testTag("staff.create.role.$option"))
            }
        }
        AppTextField(password,{if(it.length<=72)password=it},"Kata sandi sementara",Modifier.fillMaxWidth().testTag("staff.create.password"),enabled=passwordEditable,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation())
        AppTextField(confirm,{if(it.length<=72)confirm=it},"Ulangi kata sandi sementara",Modifier.fillMaxWidth().testTag("staff.create.confirm"),enabled=passwordEditable,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=PasswordVisualTransformation())
        Text("12–72 karakter, maksimal 72 byte. Sampaikan secara pribadi; staf wajib menggantinya saat masuk pertama kali. Persetujuan perangkat tetap diperlukan.")
    }
}
