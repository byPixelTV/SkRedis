package de.bypixeltv.skredis.managers

import de.bypixeltv.skredis.Main
import de.bypixeltv.skredis.events.RedisMessageEvent
import org.bukkit.scheduler.BukkitTask
import org.json.JSONArray
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
    private val config = plugin.config

    init {
        val jConfig = JedisPoolConfig()
        val maxConnections = 10

        jConfig.maxTotal = maxConnections
        jConfig.maxIdle = maxConnections
        jConfig.minIdle = 1
        jConfig.blockWhenExhausted = true

        val password = config.getString("redis.password") ?: ""
        jedisPool = if (password.isEmpty()) {
            JedisPool(
                jConfig,
                config.getString("redis.host") ?: "127.0.0.1",
                config.getInt("redis.port"),
                config.getInt("redis.timeout"),
                config.getBoolean("redis.usessl")
            )
        } else {
            JedisPool(
                jConfig,
                config.getString("redis.host") ?: "127.0.0.1",
                config.getInt("redis.port"),
                config.getInt("redis.timeout"),
                password,
                config.getBoolean("redis.usessl")
            )
        }

        channelsInByte = setupChannels()
        connectionTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, this, 0, 20 * 5)
    }

    override fun run() {
        if (!isConnectionBroken.get() || isConnecting.get()) {
            return
        }
        plugin.sendLogs("Connecting to Redis server...")
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

    fun sendMessage(message: Array<String>, channel: String) {
        val json = JSONObject()
        json.put("messages", JSONArray(message.toList()))
        json.put("action", "skript")
        json.put("date", System.currentTimeMillis())
        finishSendMessage(json, channel)
    }

    private fun finishSendMessage(json: JSONObject, channel: String) {
        try {
            val message = json.toString().toByteArray(StandardCharsets.UTF_8)

            // Sending a redis message blocks main thread if there's no more connections available
            // So to avoid issues, it's best to do it always on separate thread
            if (plugin.isEnabled) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    jedisPool.resource.use { jedis ->
                        try {
                            jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), message)
                        } catch (e: Exception) {
                            plugin.sendErrorLogs("Error sending redis message!")
                            e.printStackTrace()
                        }
                    }
                })
            } else {
                // Execute sending of redis message on the main thread if plugin is disabling
                // So it can still process the sending
                jedisPool.resource.use { jedis ->
                    try {
                        jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), message)
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

        if (plugin.config.getBoolean("redivelocity.enabled")) {
            if (!config.getBoolean("redivelocity.use-json")) {
                when (j.getString("action")) {
                    "serverSwitch", "postLogin", "disconnect" -> {
                        val ipaddress = j.getString("address")
                        val username = j.getString("name")
                        val uuid = j.getString("uuid")
                        val timestamp = j.getString("timestamp")
                        val clientbrand = try {
                             j.getString("clientbrand")
                        } catch (e: Exception) {
                            "unknown"
                        }
                        val proxyid = try {
                            j.getString("proxyid")
                        } catch (e: Exception) {
                            "unknown"
                        }
                        val messages = "$proxyid;$username;$uuid;$ipaddress;$clientbrand;$timestamp"
                        val date = System.currentTimeMillis()
                        val eventrv = RedisMessageEvent(channel, "redivelocity:${j.getString("action")};$messages", date)
                        var eventrb: RedisMessageEvent? = null
                        when (j.getString("action")) {
                            "serverSwitch" -> {
                                eventrb = RedisMessageEvent(channel, "redisbungee:SERVER_CHANGE;$messages", date)
                            }
                            "postLogin" -> {
                                eventrb = RedisMessageEvent(channel, "redisbungee:JOIN;$messages", date)
                            }
                            "disconnect" -> {
                                eventrb = RedisMessageEvent(channel, "redisbungee:LEAVE;$messages", date)
                            }
                        }
                        try {
                            if (plugin.isEnabled) {
                                if (eventrb != null) {
                                    plugin.server.pluginManager.callEvent(eventrb)
                                }
                                plugin.server.pluginManager.callEvent(eventrv)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    "skript" -> {
                        val messages = j.getJSONArray("messages")
                        for (i in 0 until messages.length()) {
                            val event = RedisMessageEvent(channel, messages.getString(i), j.getLong("date"))
                            plugin.server.pluginManager.callEvent(event)
                        }
                    }
                }
            } else {
                val event = RedisMessageEvent(channel, message, System.currentTimeMillis())
                plugin.server.pluginManager.callEvent(event)
            }
        } else {
            if (j.getString("action") == "skript") {
                val messages = j.getJSONArray("messages")
                for (i in 0 until messages.length()) {
                    val event = RedisMessageEvent(channel, messages.getString(i), j.getLong("date"))
                    plugin.server.pluginManager.callEvent(event)
                }
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
    } //

}
