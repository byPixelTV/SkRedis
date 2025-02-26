package de.bypixeltv.skredis.utils

import org.json.JSONArray
import org.json.JSONObject

object JsonUtil {
    fun String.isValidJson(): Boolean {
        return try {
            JSONObject(this)
            true
        } catch (_: Exception) {
            try {
                JSONArray(this)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun String.wrap(prefix: String, suffix: String): String = "$prefix$this$suffix"
}