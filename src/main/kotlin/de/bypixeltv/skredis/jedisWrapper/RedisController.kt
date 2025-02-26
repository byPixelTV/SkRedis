package de.bypixeltv.skredis.jedisWrapper

import de.bypixeltv.skredis.Main
import de.bypixeltv.skredis.config.ConfigLoader
import de.bypixeltv.skredis.events.RedisMessageEvent
import de.bypixeltv.skredis.utils.JsonUtil.isValidJson
import de.bypixeltv.skredis.utils.JsonUtil.wrap
import org.bukkit.scheduler.BukkitTask
import org.json.JSONObject
import redis.clients.jedis.BinaryJedisPubSub
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.JedisConnectionException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class RedisController(private val plugin: Main) : BinaryJedisPubSub(), Runnable {

    private var jedisPool: JedisPool
    private var channelsInByte: Array<ByteArray>
    private val isConnectionBroken = AtomicBoolean(true)
    private val isConnecting = AtomicBoolean(false)
    private val connectionTask: BukkitTask
    private val configLoader = ConfigLoader

    init {
        val jConfig = JedisPoolConfig()
        val maxConnections = configLoader.config?.redis?.maxConnections ?: 10

        jConfig.maxTotal = maxConnections
        jConfig.maxIdle = maxConnections
        jConfig.minIdle = 1
        jConfig.blockWhenExhausted = true

        val password = configLoader.config?.redis?.password ?: ""
        jedisPool = if (password.isEmpty()) {
            JedisPool(
                jConfig,
                configLoader.config?.redis?.host ?: "127.0.0.1",
                configLoader.config?.redis?.port ?: 6379,
                configLoader.config?.redis?.timeout ?: 9000,
                configLoader.config?.redis?.useSsl == true
            )
        } else {
            JedisPool(
                jConfig,
                configLoader.config?.redis?.host ?: "127.0.0.1",
                configLoader.config?.redis?.port ?: 6379,
                configLoader.config?.redis?.timeout ?: 9000,
                password,
                configLoader.config?.redis?.useSsl == true
            )
        }

        channelsInByte = setupChannels()
        connectionTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, this, 0, 20 * 5)
    }

    override fun run() {
        if (!isConnectionBroken.get() || isConnecting.get()) {
            return
        }
        plugin.sendInfoLogs("<yellow>Connecting to Redis server...</yellow>")
        isConnecting.set(true)
        try {
            jedisPool.resource.use { _ ->
                isConnectionBroken.set(false)
                plugin.sendInfoLogs("Connection to Redis server has established! Success!")

            }
        } catch (e: Exception) {
            isConnecting.set(false)
            isConnectionBroken.set(true)
            plugin.sendErrorLogs("Connection to Redis server has failed! Please check your details in the configuration.")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        connectionTask.cancel()
        if (this.isSubscribed) {
            try {
                this.unsubscribe()
            } catch (e: Exception) {
                plugin.sendErrorLogs("Something went wrong during unsubscribing...")
                e.printStackTrace()
            }
        }
        jedisPool.close()
    }

    fun sendMessage(message: String, channel: String) {
        try {
            val formattedMessage = if (configLoader.config?.redis?.useCustomMessageFormat == true) {
                configLoader.config?.redis?.messageFormat?.replace("%message%", message) ?: message
            } else {
                val jsonObject = JSONObject()
                jsonObject.put("message", message)
                jsonObject.put("timestamp", System.currentTimeMillis())
                jsonObject.toString()
            }

            val jsonMessageString = if (formattedMessage.isValidJson()) {
                val jsonObject = JSONObject(formattedMessage)
                jsonObject.put("action", "skript")
                jsonObject.put("timestamp", System.currentTimeMillis())
                jsonObject.toString()
            } else {
                // put { and } around the message and return it if it's not a valid JSON to make it a valid JSON
                val jsonObject = JSONObject(formattedMessage.wrap("{", "}"))
                jsonObject.put("action", "skript")
                jsonObject.put("timestamp", System.currentTimeMillis())
                jsonObject.toString()
            }

            val byteArrayMessage = jsonMessageString.toString().toByteArray(StandardCharsets.UTF_8)

            // Sending a redis message blocks the main thread if there are no more connections available,
            // so to avoid issues, it's best to do it always on a separate thread
            if (plugin.isEnabled) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    jedisPool.resource.use { jedis ->
                        try {
                            jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), byteArrayMessage)
                        } catch (e: Exception) {
                            plugin.sendInfoLogs("Error sending redis message!")
                            e.printStackTrace()
                        }
                    }
                })
            } else {
                // Execute sending of a redis message on the main thread if plugin is disabling
                // So it can still process the sending
                jedisPool.resource.use { jedis ->
                    try {
                        jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), byteArrayMessage)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (exception: JedisConnectionException) {
            exception.printStackTrace()
        }
    }

    fun processMessage(channel: String, message: String) {
        val j = JSONObject(message)

        if (configLoader.config?.redivelocity?.enabled == true) {
            when (j.getString("action")) {
                "serverSwitch", "postLogin", "disconnect" -> {
                    val event = RedisMessageEvent(channel, message, j.getString("timestamp").toLong(), true)
                    plugin.server.pluginManager.callEvent(event)
                }
                "skript" -> {
                    val messageFromJson = j.getString("message")
                    val returnedMessage = if (configLoader.config?.redis?.useCustomMessageFormat == true) {
                        message
                    } else {
                        messageFromJson
                    }
                    val event = RedisMessageEvent(channel, returnedMessage, j.getLong("timestamp"), false)
                    plugin.server.pluginManager.callEvent(event)
                }
                else -> {
                    val event = RedisMessageEvent(channel, message, System.currentTimeMillis(), false)
                    plugin.server.pluginManager.callEvent(event)
                }
            }
        } else {
            if (j.getString("action") == "skript") {
                val messageFromJson = j.getString("message")
                val returnedMessage = if (configLoader.config?.redis?.useCustomMessageFormat == true) {
                    message
                } else {
                    messageFromJson
                }
                val event = RedisMessageEvent(channel, returnedMessage, j.getLong("timestamp"), false)
                plugin.server.pluginManager.callEvent(event)
            } else {
                val event = RedisMessageEvent(channel, message, System.currentTimeMillis(), false)
                plugin.server.pluginManager.callEvent(event)
            }
        }
    }

    fun setHashField(hashName: String, fieldName: String, value: String) {
        jedisPool.resource.use { jedis ->
            val type = jedis.type(hashName)
            if (type != "hash") {
                if (type == "none") {
                    jedis.hset(hashName, fieldName, value)
                } else {
                    System.err.println("Error: Key $hashName doesn't hold a hash. It holds a $type.")
                }
            } else {
                jedis.hset(hashName, fieldName, value)
            }
        }
    }

    fun deleteHashField(hashName: String, fieldName: String) {
        jedisPool.resource.use { jedis ->
            jedis.hdel(hashName, fieldName)
        }
    }

    fun deleteHash(hashName: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(hashName)
        }
    }

    fun addToList(listName: String, values: Array<String>) {
        jedisPool.resource.use { jedis ->
            values.forEach { value ->
                jedis.rpush(listName, value)
            }
        }
    }

    fun setListValue(listName: String, index: Int, value: String) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                System.err.println("Error: Index $index does not exist in the list $listName.")
            } else {
                jedis.lset(listName, index.toLong(), value)
            }
        }
    }

    fun removeFromList(listName: String, index: Int) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                System.err.println("Error: Index $index does not exist in the list $listName.")
            } else {
                val tempKey = UUID.randomUUID().toString()
                jedis.lset(listName, index.toLong(), tempKey)
                jedis.lrem(listName, 0, tempKey)
            }
        }
    }

    fun removeFromListByValue(listName: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.lrem(listName, 0, value)
        }
    }

    fun deleteList(listName: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(listName)
        }
    }

    fun setString(key: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.set(key, value)
        }
    }

    fun getString(key: String): String? {
        return jedisPool.resource.use { jedis ->
            jedis.get(key)
        }
    }

    fun deleteString(key: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(key)
        }
    }

    fun getHashField(hashName: String, fieldName: String): String? {
        return jedisPool.resource.use { jedis ->
            jedis.hget(hashName, fieldName)
        }
    }

    fun getAllHashFields(hashName: String): Set<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.hkeys(hashName)
        }
    }

    fun getAllHashValues(hashName: String): List<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.hvals(hashName)
        }
    }

    fun getList(listName: String): List<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.lrange(listName, 0, -1)
        }
    }

    fun getHashFieldNamesByValue(hashName: String, value: String): List<String> {
        val fieldNames = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val keys = jedis.keys(hashName)
            for (key in keys) {
                val fieldsAndValues = jedis.hgetAll(key)
                for (entry in fieldsAndValues.entries) {
                    if (entry.value == value) {
                        fieldNames.add(entry.key)
                    }
                }
            }
        }
        return fieldNames
    }

    private fun setupChannels(): Array<ByteArray> {
        val channels = Main.INSTANCE.config.getStringList("Channels")

        return Array(channels.size) { channels[it].toByteArray(StandardCharsets.UTF_8) }
    }

    fun getJedisPool(): JedisPool {
        return jedisPool
    }
}
