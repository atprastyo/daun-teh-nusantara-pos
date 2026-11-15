package com.dauntehnusantara.pos.feature.catalog.photos.presentation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.dauntehnusantara.pos.feature.auth.domain.AuthRepository
import com.dauntehnusantara.pos.feature.catalog.photos.domain.ProductPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext

@HiltViewModel
class ProductPhotosViewModel @Inject constructor(val repository:ProductPhotoRepository,auth:AuthRepository):ViewModel() {
    val access=auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged()
        .stateIn(viewModelScope,SharingStarted.Eagerly,null)
}
data class PhotoAccess(val repository:ProductPhotoRepository,val generation:Any?)
val LocalProductPhotos=staticCompositionLocalOf<PhotoAccess?>{null}

@Composable
fun ProductPhoto(path:String?,name:String,modifier:Modifier=Modifier) {
    val access=LocalProductPhotos.current
    key(path,access?.generation){PhotoContent(path,name,modifier,access)}
}

@Composable
private fun PhotoContent(path:String?,name:String,modifier:Modifier,access:PhotoAccess?) {
    var loading by remember { mutableStateOf(path!=null&&access!=null) }
    var retry by remember(path,access?.generation){mutableIntStateOf(0)}
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,path,access?.generation,retry) {
        value=null;loading=path!=null&&access!=null
        if(path!=null&&access!=null) {
            val bytes=access.repository.load(path)
            value=bytes?.let{withContext(Dispatchers.IO){runCatching{BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap()}.getOrNull()}}
        }
        loading=false
    }
    if(image!=null) Image(requireNotNull(image),"Foto $name",modifier.aspectRatio(1f),contentScale=ContentScale.Crop)
    else Surface(modifier.aspectRatio(1f),color=MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.Center) {
            // Retry is the meaningful control in a missing-image thumbnail. An extra
            // icon above it consumed its 48dp touch target in an 80dp catalog card.
            if(loading){CircularProgressIndicator(Modifier.size(20.dp));Text("Memuat foto…",style=MaterialTheme.typography.labelSmall)}
            else if(path!=null&&access!=null) TextButton(onClick={retry++},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)) {
                Text("Muat foto",style=MaterialTheme.typography.labelMedium)
            }
            else {
                Icon(androidx.compose.ui.res.painterResource(com.dauntehnusantara.pos.R.drawable.ic_product_placeholder),contentDescription=null,modifier=Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                Text("Tanpa foto",style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
