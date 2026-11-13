package com.dauntehnusantara.pos.core.network

import java.net.URI
import java.util.Base64
import org.json.JSONObject

class BackendConfig(val url: String, val publicKey: String) {
    val configured: Boolean get() = valid(url, publicKey)
    companion object {
        fun valid(url: String, key: String): Boolean = runCatching {
            val uri = URI(url)
            val publicKey = key.startsWith("sb_publishable_") || runCatching {
                val parts = key.split('.')
                parts.size == 3 && JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).optString("role") == "anon"
            }.getOrDefault(false)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/") && uri.query == null && uri.fragment == null &&
                publicKey && key.none { it.isWhitespace() }
        }.getOrDefault(false)
    }
}
