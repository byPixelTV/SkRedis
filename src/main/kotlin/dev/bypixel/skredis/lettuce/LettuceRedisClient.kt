package dev.bypixel.skredis.lettuce

import dev.bypixel.skredis.config.Config
import dev.bypixel.skredis.config.ConfigLoader
import dev.bypixel.skredis.utils.SkRedisCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.IntegerOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object LettuceRedisClient {

    private val config = ConfigLoader.config?.redis ?: Config.RedisConfig()
    private val host = config.host
    private val port = config.port
    private val password = config.password
    private val useSsl = config.useSsl
    private val username = config.username

    private val redisUri = RedisURI.Builder.redis(host, port).apply {
        when {
            username.isNotBlank() && password.isNotBlank() ->
                withAuthentication(username, password.toCharArray())
            username.isBlank() && password.isNotBlank() ->
                withPassword(password.toCharArray())
            else -> {

            }
        }
        if (useSsl) {
            withSsl(true)
            withStartTls(true)
        }
    }.build()

    val redisClient: RedisClient = RedisClient.create(redisUri)
    val connection: StatefulRedisConnection<String, String> = redisClient.connect()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val commands = connection.coroutines()

    val sync: RedisCommands<String, String> = connection.sync()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun sendCustomMessage(message: JSONObject, channel: String) {
        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            connection.coroutines().publish(channel, message.toString())
        }
    }

    fun sendMessage(message: String, channel: String) {
        val json = JSONObject().apply {
            put("message", message)
            put("action", "skredis-message")
            put("date", System.currentTimeMillis())
        }
        sendCustomMessage(json, channel)
    }

    fun returnKeysWithMatchingValue(key: String, value: String): List<String> {
        return connection.sync().hkeys(key)
            .filter { field -> connection.sync().hget(key, field) == value }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun returnKeysWithMatchingValueAsync(key: String, value: String): List<String> {
        return connection.coroutines().hkeys(key)
            .filter { field -> connection.async().hget(key, field).await() == value }
            .toList()
    }

    suspend fun tryConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            connection.async().ping().await() == "PONG"
        } catch (_: Exception) {
            false
        }
    }

    fun deleteHashFieldByValue(key: String, value: String): Long {
        val fieldsToDelete = returnKeysWithMatchingValue(key, value)
        return if (fieldsToDelete.isNotEmpty()) {
            connection.sync().hdel(key, *fieldsToDelete.toTypedArray())
        } else {
            0L
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun deleteHashFieldByValueAsync(key: String, value: String): Long {
        val fieldsToDelete = returnKeysWithMatchingValueAsync(key, value)
        return if (fieldsToDelete.isNotEmpty()) {
            connection.coroutines().hdel(key, *fieldsToDelete.toTypedArray()) ?: 0L
        } else {
            0L
        }
    }

    fun findKeysWithMatchingValuesAsList(key: String): List<String> {
        return connection.sync().hvals(key)
            .filterNotNull()
            .distinct()
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun findKeysWithMatchingValuesAsListAsync(key: String): List<String> {
        return connection.coroutines().hvals(key)
            .filterNotNull()
            .toList()
            .distinct()
    }

    fun setTtlOfHashField(key: String, field: String, seconds: Long) {
        connection.sync().hexpire(key, seconds, field)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun setTtlOfHashFieldAsync(key: String, field: String, seconds: Long) {
        connection.coroutines().hexpire(key, seconds, field)
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        connection.close()
        redisClient.shutdown()
    }
}