package com.dauntehnusantara.pos.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.core.designsystem.components.AppNotice
import com.dauntehnusantara.pos.feature.staff.domain.StaffCapacity

@Composable
internal fun StaffCapacityNotice(capacity:StaffCapacity?,actorRole:String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Kuota staf outlet",style=MaterialTheme.typography.titleMedium)
        if(capacity==null)Text("Kuota belum tersedia. Perbarui daftar atau hubungi administrator Supabase.")
        else {
            if(actorRole=="OWNER")Text("Pengelola: ${capacity.managerUsed} / ${capacity.managerLimit?:"belum diatur"}")
            Text("Kasir: ${capacity.cashierUsed} / ${capacity.cashierLimit?:"belum diatur"}")
            if(!capacity.configured)AppNotice("Batas belum diatur administrator Supabase. Pembuatan akun baru diblokir.")
            else if(!capacity.anyAvailable(actorRole))AppNotice("Kuota staf penuh. Hubungi administrator Supabase untuk meninjau batas outlet.")
        }
        Text("Akun nonaktif dan pembuatan yang belum selesai tetap memakai kuota. Hanya administrator Supabase dapat mengubah batas.",style=MaterialTheme.typography.bodySmall)
    }
}
