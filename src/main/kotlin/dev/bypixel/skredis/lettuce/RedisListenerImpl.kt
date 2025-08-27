package dev.bypixel.skredis.lettuce

import dev.bypixel.skredis.Main
import dev.bypixel.skredis.events.CustomRedisMessageEvent
import dev.bypixel.skredis.events.RedisMessageEvent
import dev.bypixel.skredis.lettuce.listener.RedisListener
import org.json.JSONObject

object RedisListenerImpl : RedisListener(listenToAll = true) {
    override fun onMessage(channel: String, message: String) {
        try {
            val jMsg = JSONObject(message)
            if (jMsg.has("action")) {
                when (jMsg.getString("action")) {
                    "skredis-message" -> {
                        val msg = jMsg.getString("message")
                        val date = jMsg.getLong("date")
                        Main.instance.server.pluginManager.callEvent(RedisMessageEvent(channel, msg, date))
                    }
                }
            } else {
                Main.instance.server.pluginManager.callEvent(CustomRedisMessageEvent(channel, jMsg.toString()))
            }
        } catch (_: Exception) {
            // Ignored, not a json message
        }
    }
}