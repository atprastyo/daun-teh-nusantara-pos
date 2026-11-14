package com.dauntehnusantara.pos

import android.app.Application
import android.graphics.*
import com.dauntehnusantara.pos.feature.catalog.photos.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.data.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductPhotosTest {
    private val brand="10000000-0000-4000-8000-000000000001"
    private val product="10000000-0000-4000-8000-000000000002"
    private val scope=PullScope(SaleScope(brand,"outlet","device","user"),"OWNER","https://example.invalid")
    private val path=PhotoRules.newPath(brand,product)
    private val context get()=RuntimeEnvironment.getApplication()
    private fun source():ByteArray {
        val b=Bitmap.createBitmap(1200,600,Bitmap.Config.ARGB_8888)
        Canvas(b).apply{drawColor(Color.GREEN);drawRect(0f,0f,300f,600f,Paint().apply{color=Color.RED});drawRect(900f,0f,1200f,600f,Paint().apply{color=Color.BLUE})}
        return ByteArrayOutputStream().also{b.compress(Bitmap.CompressFormat.PNG,100,it);b.recycle()}.toByteArray()
    }
    private fun invalid(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Before fun clean(){File(context.cacheDir,"product-photos").deleteRecursively()}
    @Test fun squareJpegIsCenterCroppedBoundedAndInvalidInputRejected() {
        val bytes=PhotoImages.encode(source());PhotoImages.validate(bytes)
        val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(512,b.width);assertEquals(512,b.height)
        val pixel=b.getPixel(12,12);assertTrue(Color.green(pixel)>230);assertTrue(Color.red(pixel)<20);assertTrue(Color.blue(pixel)<20);b.recycle()
        invalid{PhotoImages.encode(byteArrayOf(1,2,3))};invalid{PhotoImages.validate(ByteArray(PhotoRules.MAX_BYTES+1))}
    }
    @Test fun referencesRejectUrlsForeignBrandsWrongProductsAndTraversal() {
        PhotoRules.path(path,brand,product)
        invalid{PhotoRules.path("https://example.invalid/$path",brand)}
        invalid{PhotoRules.path("../$path",brand)}
        invalid{PhotoRules.path(path,"20000000-0000-4000-8000-000000000001")}
        invalid{PhotoRules.path(path,brand,brand)}
        invalid{PhotoCommand(productId=product,expectedVersion=-1,path=path).validate(brand)}
    }
    private inner class Gateway(val bytes:ByteArray):ProductPhotoGateway {
        var current:PullScope?=scope;var down=false;var downloads=0;var uploads=0
        override suspend fun photoScope()=current
        override suspend fun downloadPhoto(scope:PullScope,path:String):ByteArray{downloads++;if(down)throw IOException();return bytes}
        override suspend fun uploadPhoto(scope:PullScope,path:String,bytes:ByteArray){uploads++}
        override suspend fun setPhoto(scope:PullScope,epoch:String,command:PhotoCommand)=PhotoAck(1,1)
    }
    @Test fun downloadedImagesSurviveRepositoryRecreationAndOfflineButAreIsolatedByAccountRoleAndProject():Unit=runBlocking {
        val bytes=PhotoImages.encode(source());val gateway=Gateway(bytes)
        val first=CachedProductPhotos(context,gateway);assertArrayEquals(bytes,first.load(path));assertEquals(1,gateway.downloads)
        gateway.down=true
        assertArrayEquals(bytes,CachedProductPhotos(context,gateway).load(path));assertEquals(1,gateway.downloads)
        gateway.current=scope.copy(sale=scope.sale.copy(userId="other"));assertNull(first.load(path))
        gateway.current=scope.copy(role="MANAGER");assertNull(first.load(path))
        gateway.current=scope.copy(project="https://other.invalid");assertNull(first.load(path))
        gateway.current=null;assertNull(first.load(path))
        gateway.current=scope;assertArrayEquals(bytes,first.load(path))
    }
    @Test fun corruptCacheRetriesNetworkAndMissingPhotoNeverThrowsIntoCheckout():Unit=runBlocking {
        val gateway=Gateway(PhotoImages.encode(source()));val cache=CachedProductPhotos(context,gateway)
        assertNotNull(cache.load(path));File(context.cacheDir,"product-photos").walkTopDown().first{it.extension=="jpg"}.writeBytes(byteArrayOf(1,2))
        assertNotNull(cache.load(path));assertEquals(2,gateway.downloads)
        gateway.down=true;assertNull(cache.load(PhotoRules.newPath(brand,product)))
        assertNull(cache.load("../escape.jpg"))
        gateway.current=scope.copy(role="CASHIER")
        assertTrue(runCatching{cache.upload(requireNotNull(gateway.current),path,gateway.bytes)}.isFailure);assertEquals(0,gateway.uploads)
    }
    @Test fun unavailableDiskCacheDoesNotFailUploadOrDisplay():Unit=runBlocking {
        File(context.cacheDir,"product-photos").writeText("simulate unavailable cache directory")
        val bytes=PhotoImages.encode(source());val gateway=Gateway(bytes);val cache=CachedProductPhotos(context,gateway)
        cache.upload(scope,path,bytes);assertEquals(1,gateway.uploads)
        assertArrayEquals(bytes,cache.load(path))
    }
    @Test fun jpegOrientationIsAppliedAndSourceGpsMetadataIsNotCopied() {
        val b=Bitmap.createBitmap(600,1200,Bitmap.Config.ARGB_8888)
        Canvas(b).apply{drawColor(Color.RED);drawRect(0f,600f,600f,1200f,Paint().apply{color=Color.BLUE})}
        val f=File(context.cacheDir,"orientation-input.jpg")
        f.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()
        android.media.ExifInterface(f.absolutePath).apply{
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6")
            setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE,"6/1,12/1,0/1")
            setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF,"S");saveAttributes()
        }
        val bytes=PhotoImages.encode(f.readBytes());val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertTrue(Color.blue(image.getPixel(64,64))>230);assertTrue(Color.red(image.getPixel(448,448))>230);image.recycle()
        val exif=android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
        assertNull(exif.getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE));f.delete()
    }

}
