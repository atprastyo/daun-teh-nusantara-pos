package com.dauntehnusantara.pos.core.network

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthHttpFailure(val status: Int,val code:String?=null) : Exception("Authentication request denied")
interface AuthTransport {
    suspend fun request(path: String, body: String? = null, token: String? = null, method: String = "POST"): String
}
@Singleton
class JsonHttpClient @Inject constructor(private val config: BackendConfig) : AuthTransport {
    override suspend fun request(path: String, body: String?, token: String?, method: String): String = withContext(Dispatchers.IO) {
        check(config.configured)
        val connection = URL(config.url + path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.setRequestProperty("apikey", config.publicKey)
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Only retain known, non-sensitive domain codes. Never expose/log server bodies.
                val code=readStaffFailureCode(connection.errorStream)
                throw AuthHttpFailure(status,code)
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer); if (size < 0) break
                    if (output.size() + size > 1048576) throw IllegalStateException("Invalid oversized authorization response")
                    output.write(buffer, 0, size)
                }
            }
            output.toString("UTF-8")
        } finally { connection.disconnect() }
    }
}

/** Strict bounded allowlist: response details can contain secrets and must be discarded. */
internal fun readStaffFailureCode(stream:java.io.InputStream?):String?=runCatching {
    stream?.use {
        val buffer=ByteArray(8193);var length=0
        while(length<buffer.size){val n=it.read(buffer,length,buffer.size-length);if(n<0)break;length+=n}
        if(length>8192)null else org.json.JSONObject(String(buffer,0,length,Charsets.UTF_8)).optString("code")
            .takeIf{code->code in setOf("STAFF_LIMIT_REACHED","STAFF_LIMITS_UNCONFIGURED")}
    }
}.getOrNull()
