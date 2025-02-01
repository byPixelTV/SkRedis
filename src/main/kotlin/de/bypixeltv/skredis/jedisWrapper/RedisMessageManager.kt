package de.bypixeltv.skredis.jedisWrapper

import de.bypixeltv.skredis.Main
import redis.clients.jedis.JedisPubSub

object RedisMessageManager {
    private var redis = Main.INSTANCE.getRC()?.getJedisPool()
    private var jedisPubSub: JedisPubSub? = null

    init {
        var channels = mutableListOf("redisbungee-data", "redisvelocity-players")
        val cchannels = Main.INSTANCE.config.getStringList("channels")
        channels.addAll(cchannels)

        jedisPubSub = object : JedisPubSub() {
            override fun onPMessage(pattern: String, channel: String, message: String) {
                if (channels.contains(channel)) {
                    Main.INSTANCE.getRC()?.processMessage(channel, message)
                }
            }

            override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
                super.onPSubscribe(pattern, subscribedChannels)
            }
        }

        // Run the subscription in a new thread
        Thread {
            try {
                redis?.resource?.use { jedis ->
                    jedis.psubscribe(jedisPubSub, *channels.toTypedArray())
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