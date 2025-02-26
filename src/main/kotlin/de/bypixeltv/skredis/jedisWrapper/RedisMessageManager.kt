package de.bypixeltv.skredis.jedisWrapper

import de.bypixeltv.skredis.Main
import de.bypixeltv.skredis.config.ConfigLoader
import redis.clients.jedis.JedisPubSub

object RedisMessageManager {
    private var redis = Main.INSTANCE.getRC()?.getJedisPool()
    private var jedisPubSub: JedisPubSub? = null
    private var configLoader: ConfigLoader = ConfigLoader

    init {
        jedisPubSub = object : JedisPubSub() {
            override fun onPMessage(pattern: String, channel: String, message: String) {
                val configuredChannels = mutableListOf("redivelocity-players")
                configuredChannels.addAll(configLoader.config?.channels ?: emptyList())
                // Only process messages from configured channels
                if (configuredChannels.contains(channel)) {
                    Main.INSTANCE.getRC()?.processMessage(channel, message)
                }
            }

            override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
                super.onPSubscribe(pattern, subscribedChannels)
            }
        }

        Thread {
            try {
                redis?.resource?.use { jedis ->
                    // Subscribe to all channels with a wildcard pattern
                    jedis.psubscribe(jedisPubSub, "*")
                }
            } catch (e: Exception) {
                Main.INSTANCE.sendErrorLogs("Error while subscribing to Redis: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    fun unsubscribe() {
        jedisPubSub?.punsubscribe()
    }
}