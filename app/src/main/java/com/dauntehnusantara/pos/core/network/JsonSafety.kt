package com.dauntehnusantara.pos.core.network

import org.json.JSONArray
import org.json.JSONObject

/** Validate nesting before recursive Android JSON parsing. Never include input in errors. */
object JsonSafety {
    fun check(raw: String, maxBytes: Int = 1048576) {
        require(raw.length <= maxBytes && raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Invalid JSON size" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Invalid JSON depth" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid JSON structure" } }
            }
        }
        require(!quoted && depth == 0) { "Invalid JSON structure" }
    }
    fun obj(raw: String, maxBytes: Int = 1048576): JSONObject { check(raw, maxBytes); return JSONObject(raw) }
    fun array(raw: String): JSONArray { check(raw); return JSONArray(raw) }
}
