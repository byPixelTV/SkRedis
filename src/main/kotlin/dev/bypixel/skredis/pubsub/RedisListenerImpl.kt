package dev.bypixel.skredis.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.events.CustomRedisMessageEvent
import dev.bypixel.skredis.events.RedisMessageEvent
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
                        SkRedis.instance.server.pluginManager.callEvent(RedisMessageEvent(channel, msg, date))
                    }
                }
            } else {
                SkRedis.instance.server.pluginManager.callEvent(CustomRedisMessageEvent(channel, jMsg.toString()))
            }
        } catch (_: Exception) {
            // Ignored, not a json message
        }
    }
}