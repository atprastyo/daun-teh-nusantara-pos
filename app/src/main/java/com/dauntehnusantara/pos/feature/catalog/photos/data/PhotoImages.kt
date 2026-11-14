package com.dauntehnusantara.pos.feature.catalog.photos.data

import android.content.ContentResolver
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import com.dauntehnusantara.pos.feature.catalog.photos.domain.PhotoRules
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoImages {
    fun validate(bytes:ByteArray) {
        require(bytes.size in 1..PhotoRules.MAX_BYTES)
        val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,o)
        require(o.outWidth==PhotoRules.EDGE&&o.outHeight==PhotoRules.EDGE&&o.outMimeType=="image/jpeg")
    }
    suspend fun prepare(resolver:ContentResolver,uri:Uri):ByteArray=withContext(Dispatchers.IO) {
        require(uri.scheme=="content")
        val source=resolver.openInputStream(uri)?.use{input->val out=ByteArrayOutputStream();val buf=ByteArray(8192)
            while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=16*1024*1024);out.write(buf,0,n)};out.toByteArray()}?:error("Cannot open image")
        encode(source)
    }
    fun encode(source:ByteArray):ByteArray {
        require(source.size in 1..16*1024*1024)
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(source,0,source.size,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=40_000_000)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2
        val decoded=requireNotNull(BitmapFactory.decodeByteArray(source,0,source.size,BitmapFactory.Options().apply{inSampleSize=sample}))
        var oriented:Bitmap=decoded
        val output=Bitmap.createBitmap(PhotoRules.EDGE,PhotoRules.EDGE,Bitmap.Config.ARGB_8888)
        try {
            val orientation=runCatching{ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
            val matrix=Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
                5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)}}
            if(!matrix.isIdentity)oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
            val side=minOf(oriented.width,oriented.height);val left=(oriented.width-side)/2;val top=(oriented.height-side)/2
            Canvas(output).apply{drawColor(Color.WHITE);drawBitmap(oriented,Rect(left,top,left+side,top+side),Rect(0,0,PhotoRules.EDGE,PhotoRules.EDGE),Paint(Paint.FILTER_BITMAP_FLAG))}
            for(quality in listOf(85,75,65,50)){
                val out=ByteArrayOutputStream();check(output.compress(Bitmap.CompressFormat.JPEG,quality,out));val bytes=out.toByteArray()
                if(bytes.size<=PhotoRules.MAX_BYTES){validate(bytes);return bytes}
            }
            error("Image too large")
        }finally{output.recycle();if(oriented!==decoded)oriented.recycle();decoded.recycle()}
    }
}

class AndroidPhotoProcessor @javax.inject.Inject constructor(@param:dagger.hilt.android.qualifiers.ApplicationContext private val context:android.content.Context):com.dauntehnusantara.pos.feature.catalog.photos.domain.ProductPhotoProcessor {
    override suspend fun prepare(uri:String)=PhotoImages.prepare(context.contentResolver,Uri.parse(uri))
}
