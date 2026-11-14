package com.dauntehnusantara.pos.feature.catalog.photos.data

import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.feature.catalog.photos.domain.PhotoRules
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PhotoTransport {
    suspend fun transfer(token: String, path: String, body: ByteArray? = null): ByteArray
}
object UnconfiguredPhotoTransport: PhotoTransport {
    override suspend fun transfer(token:String,path:String,body:ByteArray?):ByteArray = error("Photo transport not configured")
}
class PhotoHttpClient @Inject constructor(private val config:BackendConfig): PhotoTransport {
    override suspend fun transfer(token:String,path:String,body:ByteArray?):ByteArray = withContext(Dispatchers.IO) {
        check(config.configured)
        require(path.startsWith("/storage/v1/object/"))
        val c=URL(config.url+path).openConnection() as HttpsURLConnection
        try {
            c.requestMethod=if(body==null)"GET" else "POST";c.instanceFollowRedirects=false;c.useCaches=false
            c.connectTimeout=5000;c.readTimeout=5000
            c.setRequestProperty("apikey",config.publicKey);c.setRequestProperty("Authorization","Bearer $token")
            if(body!=null){require(body.size in 1..PhotoRules.MAX_BYTES);c.doOutput=true;c.setFixedLengthStreamingMode(body.size)
                c.setRequestProperty("Content-Type","image/jpeg");c.setRequestProperty("x-upsert","false")
                c.outputStream.use{it.write(body)}}
            val status=c.responseCode
            if(status !in 200..299){c.errorStream?.close();throw AuthHttpFailure(status)}
            val out=ByteArrayOutputStream()
            c.inputStream.use{input->val buffer=ByteArray(4096);while(true){val n=input.read(buffer);if(n<0)break
                require(out.size()+n<=PhotoRules.MAX_BYTES);out.write(buffer,0,n)}}
            out.toByteArray()
        }finally{c.disconnect()}
    }
}
