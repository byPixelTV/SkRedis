package dev.bypixel.skredis.jedisWrapper

import dev.bypixel.skredis.SkRedisLogger
import dev.bypixel.skredis.config.ConfigLoader
import dev.bypixel.skredis.events.RedisMessageEvent
import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import redis.clients.jedis.BinaryJedisPubSub
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.JedisConnectionException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RedisController(private val plugin: JavaPlugin) : BinaryJedisPubSub(), CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val jedisPool: JedisPool
    private val isConnectionBroken = AtomicBoolean(true)
    private val isConnecting = AtomicBoolean(false)
    private var connectionJob: Job? = null
    private val configLoader = ConfigLoader

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    init {
        val jConfig = JedisPoolConfig()
        val maxConnections = 30

        jConfig.maxTotal = maxConnections
        jConfig.maxIdle = maxConnections
        jConfig.testOnBorrow = true
        jConfig.minIdle = 5
        jConfig.blockWhenExhausted = true
        jConfig.setMaxWait(Duration.ofMillis(2000))


        val password = configLoader.config?.redis?.password ?: ""
        val username = configLoader.config?.redis?.username
        val useUsername = username != null && username != "default" && username.isNotEmpty()

        jedisPool = when {
            useUsername && password.isNotEmpty() -> {
                JedisPool(
                    jConfig,
                    configLoader.config?.redis?.host ?: "127.0.0.1",
                    configLoader.config?.redis?.port ?: 6379,
                    configLoader.config?.redis?.timeout ?: 9000,
                    username,
                    password,
                    configLoader.config?.redis?.useSsl == true
                )
            }
            password.isNotEmpty() -> {
                JedisPool(
                    jConfig,
                    configLoader.config?.redis?.host ?: "127.0.0.1",
                    configLoader.config?.redis?.port ?: 6379,
                    configLoader.config?.redis?.timeout ?: 9000,
                    password,
                    0,
                    configLoader.config?.redis?.useSsl == true
                )
            }
            else -> {
                JedisPool(
                    jConfig,
                    configLoader.config?.redis?.host ?: "127.0.0.1",
                    configLoader.config?.redis?.port ?: 6379,
                    configLoader.config?.redis?.timeout ?: 9000,
                    null,
                    configLoader.config?.redis?.useSsl == true
                )
            }
        }

        startConnectionTask()
    }

    private fun startConnectionTask() {
        connectionJob = launch {
            while (isActive) {
                delay(20 * 5 * 1000L)
                runAsync()
            }
        }
    }

    fun runAsync(): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            if (!isConnectionBroken.get() || isConnecting.get()) {
                return@runAsync
            }
            SkRedisLogger.info(plugin, "Attempting to reconnect to Redis server...")
            isConnecting.set(true)
            try {
                jedisPool.resource.use { jedis ->
                    isConnectionBroken.set(false)
                    SkRedisLogger.success(plugin, "Successfully reconnected to Redis server!")
                }
            } catch (e: Exception) {
                isConnecting.set(false)
                isConnectionBroken.set(true)
                SkRedisLogger.error(plugin, "Connection to Redis server has failed! Please check your details in the configuration.")
                e.printStackTrace()
            }
        }, executor)
    }

    fun sendMessageAsync(message: String, channel: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            val json = JSONObject()
            json.put("message", message)
            json.put("action", "skript")
            json.put("timestamp", System.currentTimeMillis())
            finishSendMessageAsync(json, channel)
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

    fun sendCustomMessageFormatAsync(json: JSONObject, channel: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try {
                val message = json.toString().toByteArray(StandardCharsets.UTF_8)
                jedisPool.resource.use { jedis ->
                    jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    private fun finishSendMessageAsync(json: JSONObject, channel: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try {
                val message = json.toString().toByteArray(StandardCharsets.UTF_8)
                jedisPool.resource.use { jedis ->
                    jedis.publish(channel.toByteArray(StandardCharsets.UTF_8), message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    fun shutdown() {
        connectionJob?.cancel()
        if (this.isSubscribed) {
            try {
                this.unsubscribe()
            } catch (e: Exception) {
                SkRedisLogger.error(plugin, "Something went wrong during unsubscribing...")
                e.printStackTrace()
            }
        }
        jedisPool.close()
        executor.shutdown() // Executor beenden
    }

    fun setStringAsync(key: String, value: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try {
                jedisPool.resource.use { jedis ->
                    jedis.set(key, value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    fun getStringAsync(key: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            jedisPool.resource.use { jedis ->
                jedis.get(key)
            }
        }
    }

    fun getKeyByValue(hashName: String, value: String) : String? {
        jedisPool.resource.use { jedis ->
            val keys = jedis.hkeys(hashName)
            for (key in keys) {
                if (jedis.hget(hashName, key) == value) {
                    return key
                }
            }
        }
        return null
    }

    fun getKeyByValueAsync(hashName: String, value: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                val keys = jedis.hkeys(hashName)
                for (key in keys) {
                    if (jedis.hget(hashName, key) == value) {
                        return@supplyAsync key
                    }
                }
                null
            }
        }, executor)
    }

    fun deleteStringAsync(key: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                jedis.del(key)
            }
        }, executor)
    }

    fun sendMessage(message: String, channel: String) {
        val json = JSONObject()
        json.put("message", message)
        json.put("action", "skript")
        json.put("timestamp", System.currentTimeMillis())
        finishSendMessage(json, channel)
    }

    fun sendCustomMessageFormat(json: JSONObject, channel: String) {
        try {
            val message = json.toString().toByteArray(StandardCharsets.UTF_8)

            launch(Dispatchers.IO) {
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

    private fun finishSendMessage(json: JSONObject, channel: String) {
        try {
            val message = json.toString().toByteArray(StandardCharsets.UTF_8)

            launch(Dispatchers.IO) {
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

    // Example of a simplified hash operation for diagnostic purposes
    fun testHashOperation() {
        println("Testing hash operation...")
        try {
            jedisPool.resource.use { jedis ->
                val testHashName = "testHash_${System.currentTimeMillis()}" // Unique hash name
                jedis.hset(testHashName, "testField", "testValue")
                SkRedisLogger.success(plugin, "Hash operation successful for $testHashName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeFromListByValue(listName: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.lrem(listName, 0, value)
        }
    }

    fun setHashField(hashName: String, fieldName: String, value: String) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.hset(hashName, fieldName, value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteHashField(hashName: String, fieldName: String) {
        jedisPool.resource.use { jedis ->
            jedis.hdel(hashName, fieldName)
        }
    }

    fun createHashFromMap(hashName: String, values: Map<String, String>) {
        if (values.isEmpty()) {
            return
        }

        try {
            jedisPool.resource.use { jedis ->
                jedis.hmset(hashName, values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createListFromList(listName: String, values: List<String>) {
        jedisPool.resource.use { jedis ->
            jedis.rpush(listName, *values.toTypedArray())
        }
    }

    fun deleteEntriesFromArray(hashName: String, keys: Array<String>) {
        if (keys.isNotEmpty()) {
            jedisPool.resource.use { jedis ->
                jedis.hdel(hashName, *keys)
            }
        }
    }

    fun deleteHash(hashName: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(hashName)
        }
    }

    fun addToList(listName: String, values: String) {
        jedisPool.resource.use { jedis ->
            jedis.rpush(listName, values)
        }
    }

    fun addMultipleToList(listName: String, values: List<String>) {
        if (values.isNotEmpty()) {
            jedisPool.resource.use { jedis ->
                jedis.rpush(listName, *values.toTypedArray())
            }
        }
    }

    fun addMultipleToListAsync(listName: String, values: List<String>): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            if (values.isNotEmpty()) {
                jedisPool.resource.use { jedis ->
                    jedis.rpush(listName, *values.toTypedArray())
                }
            }
        }, executor)
    }

    fun setListValue(listName: String, index: Int, value: String) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                SkRedisLogger.error(plugin, "Error: Index $index does not exist in the list $listName.")
            } else {
                jedis.lset(listName, index.toLong(), value)
            }
        }
    }

    fun setListValueAsync(listName: String, index: Int, value: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                val listLength = jedis.llen(listName)
                if (index >= listLength) {
                    SkRedisLogger.error(plugin, "Error: Index $index does not exist in the list $listName.")
                } else {
                    jedis.lset(listName, index.toLong(), value)
                }
            }
        }, executor)
    }

    fun getHashValuesAsPair(hashName: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        jedisPool.resource.use { jedis ->
            val keys = jedis.hkeys(hashName)
            for (key in keys) {
                values[key] = jedis.hget(hashName, key)
            }
        }
        return values
    }

    fun incrementHashField(hashName: String, fieldName: String, increment: Long = 1): Long {
        return jedisPool.resource.use { jedis ->
            jedis.hincrBy(hashName, fieldName, increment)
        }
    }

    fun incrementHashFieldAsync(hashName: String, fieldName: String, increment: Long = 1): CompletableFuture<Long> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.hincrBy(hashName, fieldName, increment)
            }
        }, executor)
    }

    fun getHashValuesAsPairSorted(hashName: String): Map<String, String> {
        val keys = mutableListOf<String>()
        val values = LinkedHashMap<String, String>()

        jedisPool.resource.use { jedis ->
            // Alle Schlüssel holen
            keys.addAll(jedis.hkeys(hashName))

            // Schlüssel alphabetisch sortieren
            keys.sort()

            // Werte in sortierter Reihenfolge einfügen
            for (key in keys) {
                values[key] = jedis.hget(hashName, key)
            }
        }

        return values
    }

    fun getHashValuesAsPairSortedAsync(hashName: String): CompletableFuture<Map<String, String>> {
        return CompletableFuture.supplyAsync({
            val keys = mutableListOf<String>()
            val values = LinkedHashMap<String, String>()

            jedisPool.resource.use { jedis ->
                // Alle Schlüssel holen
                keys.addAll(jedis.hkeys(hashName))

                // Schlüssel alphabetisch sortieren
                keys.sort()

                // Werte in sortierter Reihenfolge einfügen
                for (key in keys) {
                    values[key] = jedis.hget(hashName, key)
                }
            }

            values
        }, executor)
    }

    fun deleteHashFieldByValue(hashName: String, value: String) {
        jedisPool.resource.use { jedis ->
            val keys = jedis.hkeys(hashName)
            for (key in keys) {
                if (jedis.hget(hashName, key) == value) {
                    jedis.hdel(hashName, key)
                }
            }
        }
    }

    fun returnKeysWithMatchingValue(hashName: String, value: String): List<String> {
        val keys = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            for (key in allKeys) {
                if (jedis.hget(hashName, key) == value) {
                    keys.add(key)
                }
            }
        }
        return keys
    }

    fun returnValuesWithMatchingKey(hashName: String, key: String): List<String> {
        val values = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            if (allKeys.contains(key)) {
                values.add(jedis.hget(hashName, key))
            }
        }
        return values
    }

    fun findKeysWithMatchingValues(hashName: String): Map<String, List<String>> {
        val matchingKeys = mutableMapOf<String, MutableList<String>>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            for (key in allKeys) {
                val value = jedis.hget(hashName, key)
                if (matchingKeys.containsKey(value)) {
                    matchingKeys[value]?.add(key)
                } else {
                    matchingKeys[value] = mutableListOf(key)
                }
            }
        }
        return matchingKeys
    }

    fun findValuesWithMatchingKeys(hashName: String): Map<String, List<String>> {
        val matchingValues = mutableMapOf<String, MutableList<String>>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            for (key in allKeys) {
                val value = jedis.hget(hashName, key)
                if (matchingValues.containsKey(key)) {
                    matchingValues[key]?.add(value)
                } else {
                    matchingValues[key] = mutableListOf(value)
                }
            }
        }
        return matchingValues
    }

    fun getAllHashNamesByRegex(regex: String): List<String> {
        val matchingKeys = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.keys(regex)
            for (key in allKeys) {
                if (jedis.type(key) == "hash") {
                    matchingKeys.add(key)
                }
            }
        }
        return matchingKeys
    }

    fun getAllHashNamesByRegexAsync(regex: String): CompletableFuture<List<String>> {
        return CompletableFuture.supplyAsync({
            val matchingKeys = mutableListOf<String>()
            jedisPool.resource.use { jedis ->
                val allKeys = jedis.keys(regex)
                for (key in allKeys) {
                    if (jedis.type(key) == "hash") {
                        matchingKeys.add(key)
                    }
                }
            }
            matchingKeys
        }, executor)
    }

    fun setTtlOfKey(key: String, ttl: Long) {
        jedisPool.resource.use { jedis ->
            jedis.expire(key, ttl)
        }
    }

    fun setHashFieldWithTTL(hashKey: String, field: String, value: String, ttlInSeconds: Long) {
        jedisPool.resource.use { jedis ->
            jedis.hset(hashKey, field, value)
            jedis.hexpire(hashKey, ttlInSeconds, field)
        }
    }

    fun setTtlOfHashField(hashKey: String, field: String, ttl: Long) {
        jedisPool.resource.use { jedis ->
            jedis.hexpire(hashKey, ttl, field)
        }
    }

    fun findKeysWithMatchingValuesAsList(hashName: String): List<String> {
        val matchingKeys = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            val valueToKeysMap = mutableMapOf<String, MutableList<String>>()

            for (key in allKeys) {
                val value = jedis.hget(hashName, key)
                if (valueToKeysMap.containsKey(value)) {
                    valueToKeysMap[value]?.add(key)
                } else {
                    valueToKeysMap[value] = mutableListOf(key)
                }
            }

            for ((_, keys) in valueToKeysMap) {
                if (keys.size > 1) {
                    matchingKeys.addAll(keys)
                }
            }
        }
        return matchingKeys
    }

    fun findValuesWithMatchingKeysAsList(hashName: String): List<String> {
        val matchingValues = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val allKeys = jedis.hkeys(hashName)
            for (key in allKeys) {
                val value = jedis.hget(hashName, key)
                if (!matchingValues.contains(key)) {
                    matchingValues.add(key)
                }
            }
        }
        return matchingValues
    }

    fun removeFromListByIndex(listName: String, index: Int) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                SkRedisLogger.error(plugin, "Error: Index $index does not exist in the list $listName.")
            } else {
                val tempKey = UUID.randomUUID().toString()
                jedis.lset(listName, index.toLong(), tempKey)
                jedis.lrem(listName, 0, tempKey)
            }
        }
    }

    fun removeFromList(listName: String, value: String) {
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
        try {
            jedisPool.resource.use { jedis ->
                jedis.set(key, value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        jedisPool.resource.use { jedis ->
            return jedis.hget(hashName, fieldName)
        }
    }

    fun getHashFieldNameByValue(hashName: String, value: String): String? {
        jedisPool.resource.use { jedis ->
            val keys = jedis.hkeys(hashName)
            for (key in keys) {
                if (jedis.hget(hashName, key) == value) {
                    return key
                }
            }
        }
        return null
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

    fun exists(key: String): Boolean {
        return jedisPool.resource.use { jedis ->
            jedis.exists(key)
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

    fun getJedisPool(): JedisPool {
        return jedisPool
    }

    fun existsAsync(key: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.exists(key)
            }
        }, executor)
    }

    fun setTtlOfKeyAsync(key: String, ttl: Long): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            jedisPool.resource.use { jedis ->
                jedis.expire(key, ttl)
            }
        }
    }

    fun setHashFieldWithTTLAsync(hashKey: String, field: String, value: String, ttlInSeconds: Long): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            jedisPool.resource.use { jedis ->
                jedis.hset(hashKey, field, value)
                jedis.hexpire(hashKey, ttlInSeconds, field)
            }
        }
    }

    fun addToListAsync(listName: String, values: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                jedis.rpush(listName, values)
            }
        }, executor)
    }

    fun removeFromListByValueAsync(listName: String, value: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            jedisPool.resource.use { jedis ->
                jedis.lrem(listName, 0, value)
            }
        }
    }

    fun getListAsync(listName: String): CompletableFuture<List<String>?> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.lrange(listName, 0, -1)
            }
        }, executor)
    }

    fun deleteListAsync(listName: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                jedis.del(listName)
            }
        }, executor)
    }

    fun setHashFieldAsync(hashName: String, fieldName: String, value: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            try {
                jedisPool.resource.use { jedis ->
                    jedis.hset(hashName, fieldName, value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteHashFieldAsync(hashName: String, fieldName: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                jedis.hdel(hashName, fieldName)
            }
        }, executor)
    }

    fun deleteHashAsync(hashName: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            jedisPool.resource.use { jedis ->
                jedis.del(hashName)
            }
        }, executor)
    }

    fun getHashFieldAsync(hashName: String, fieldName: String): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.hget(hashName, fieldName)
            }
        }, executor)
    }

    fun getAllHashFieldsAsync(hashName: String): CompletableFuture<Set<String>?> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.hkeys(hashName)
            }
        }, executor)
    }

    fun getAllHashValuesAsync(hashName: String): CompletableFuture<List<String>?> {
        return CompletableFuture.supplyAsync({
            jedisPool.resource.use { jedis ->
                jedis.hvals(hashName)
            }
        }, executor)
    }

    fun getHashValuesAsPairAsync(hashName: String): CompletableFuture<Map<String, String>> {
        return CompletableFuture.supplyAsync({
            val values = mutableMapOf<String, String>()
            jedisPool.resource.use { jedis ->
                val keys = jedis.hkeys(hashName)
                for (key in keys) {
                    values[key] = jedis.hget(hashName, key)
                }
            }
            values
        }, executor)
    }
}