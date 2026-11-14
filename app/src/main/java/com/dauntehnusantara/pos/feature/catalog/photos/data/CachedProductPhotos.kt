package com.dauntehnusantara.pos.feature.catalog.photos.data

import android.content.Context
import android.util.AtomicFile
import com.dauntehnusantara.pos.feature.catalog.photos.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException

@Singleton
class CachedProductPhotos @Inject constructor(@ApplicationContext context:Context,private val gateway:ProductPhotoGateway):ProductPhotoRepository {
    private val root=File(context.cacheDir,"product-photos")
    private val files=Mutex();private val downloads=Semaphore(3)
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun file(s:PullScope,p:String)=File(File(root,hash(s.key)),hash(p)+".jpg")
    override suspend fun currentScope()=gateway.photoScope()
    private suspend fun write(s:PullScope,p:String,bytes:ByteArray)=withContext(Dispatchers.IO){files.withLock {
        val f=file(s,p);check(f.parentFile!!.mkdirs()||f.parentFile!!.isDirectory)
        val atomic=AtomicFile(f);val stream=atomic.startWrite()
        try{stream.write(bytes);atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
        val all=root.walkTopDown().filter{it.isFile&&it.extension=="jpg"}.toList();var total=all.sumOf{it.length()}
        all.sortedBy{it.lastModified()}.forEach{if(total>32L*1024*1024&&it!=f){val n=it.length();if(it.delete())total-=n}}
    }}
    private suspend fun cache(s:PullScope,p:String,bytes:ByteArray) {
        try{write(s,p,bytes)}catch(e:CancellationException){throw e}catch(_:Exception){/* Cache is optional, including disk-full failures. */}
    }
    override suspend fun upload(scope:PullScope,path:String,bytes:ByteArray) {
        require(scope.role in listOf("OWNER","MANAGER"));PhotoRules.path(path,scope.sale.brandId);PhotoImages.validate(bytes)
        require(scope==gateway.photoScope())
        gateway.uploadPhoto(scope,path,bytes)
        if(scope==gateway.photoScope())cache(scope,path,bytes)
    }
    override suspend fun load(path:String):ByteArray? {
        val s=gateway.photoScope()?:return null
        return try {
            PhotoRules.path(path,s.sale.brandId)
            val saved=withContext(Dispatchers.IO){files.withLock{val f=file(s,path)
                if(!f.isFile)null else runCatching{require(f.length()<=PhotoRules.MAX_BYTES);f.readBytes().also{PhotoImages.validate(it);f.setLastModified(System.currentTimeMillis())}}.getOrElse{f.delete();null}}}
            val result=saved?:downloads.withPermit{gateway.downloadPhoto(s,path).also{PhotoImages.validate(it);if(s==gateway.photoScope())cache(s,path,it)}}
            result.takeIf{s==gateway.photoScope()}
        }catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
}
