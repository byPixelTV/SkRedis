package dev.bypixel.skredis.jedisWrapper

import dev.bypixel.skredis.Main
import dev.bypixel.skredis.SkRedisLogger
import dev.bypixel.skredis.config.ConfigLoader
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.exceptions.JedisConnectionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RedisMessageManager {
    private var jedisPool: JedisPool? = null
    private var jedisPubSub: AtomicReference<JedisPubSub?> = AtomicReference(null)
    private var configLoader: ConfigLoader = ConfigLoader
    private var pubSubThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private var isInitialized = false
    private val operationLock = ReentrantLock()

    fun initialize() {
        operationLock.withLock {
            // Always perform a full shutdown first to ensure cleanup
            shutdownInternal(false)

            // Update pool reference
            jedisPool = Main.INSTANCE.getRC()?.getJedisPool()

            // Start new connection
            startPubSubInternal()

            // Register shutdown hook for clean termination if not already initialized
            if (!isInitialized) {
                Runtime.getRuntime().addShutdownHook(Thread {
                    shutdownInternal(true)
                })
                isInitialized = true
            }
        }
    }

    private fun shutdownInternal(isSystemShutdown: Boolean) {
        try {
            // Get current PubSub instance first
            val currentPubSub = jedisPubSub.getAndSet(null)
            val plugin = Main.INSTANCE

            // Stop the thread first
            val thread = pubSubThread
            if (thread != null) {
                if (isSystemShutdown) {
                    SkRedisLogger.info(plugin, "Shutting down Redis PubSub thread (system shutdown)")
                } else {
                    SkRedisLogger.info(plugin, "Shutting down Redis PubSub thread")
                }

                thread.interrupt()
                try {
                    thread.join(3000) // Give it a reasonable time to finish
                } catch (e: InterruptedException) {
                    if (!isSystemShutdown) {
                        SkRedisLogger.error(Main.INSTANCE, "Error waiting for PubSub thread to finish: ${e.message}")
                    }
                    Thread.currentThread().interrupt()
                }
            }

            // Now try to unsubscribe if needed
            if (currentPubSub != null && isConnected.get()) {
                try {
                    currentPubSub.punsubscribe()
                    SkRedisLogger.info(plugin, "Unsubscribed from Redis PubSub channels")
                } catch (e: Exception) {
                    if (!isSystemShutdown) {
                        SkRedisLogger.error(Main.INSTANCE, "Error unsubscribing: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            if (!isSystemShutdown) {
                SkRedisLogger.error(Main.INSTANCE, "Error during shutdown: ${e.message}")
            }
        } finally {
            isRunning.set(false)
            isConnected.set(false)
            pubSubThread = null
        }
    }

    private fun createJedisPubSub(): JedisPubSub {
        return object : JedisPubSub() {
            override fun onPMessage(pattern: String, channel: String, message: String) {
                val configuredChannels = mutableListOf("redivelocity-players")
                configuredChannels.addAll(configLoader.config?.channels ?: emptyList())
                if (configuredChannels.contains(channel)) {
                    Main.INSTANCE.getRC()?.processMessage(channel, message)
                }
            }

            override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
                isConnected.set(true)
                SkRedisLogger.info(Main.INSTANCE, "Subscribed to pattern: $pattern")
                if (subscribedChannels > 0) {
                    SkRedisLogger.info(Main.INSTANCE, "Subscribed to $subscribedChannels channels")
                } else {
                    SkRedisLogger.info(Main.INSTANCE, "Subscribed to all channels")
                }
            }

            override fun onPUnsubscribe(pattern: String, subscribedChannels: Int) {
                if (subscribedChannels == 0) {
                    isConnected.set(false)
                    SkRedisLogger.info(Main.INSTANCE, "Unsubscribed from all channels")
                }
            }
        }
    }

    private fun startPubSubInternal() {
        // We don't need to check isRunning here because shutdownInternal is always called before

        // Update the pool reference
        jedisPool = Main.INSTANCE.getRC()?.getJedisPool()
        val newPubSub = createJedisPubSub()
        jedisPubSub.set(newPubSub)

        isRunning.set(true)
        pubSubThread = Thread {
            try {
                jedisPool?.resource?.use { jedis ->
                    SkRedisLogger.info(Main.INSTANCE, "Starting Redis PubSub subscription")
                    jedis.psubscribe(newPubSub, "*")
                }
            } catch (e: JedisConnectionException) {
                SkRedisLogger.error(Main.INSTANCE, "Redis connection error: ${e.message}")
            } catch (e: Exception) {
                SkRedisLogger.error(Main.INSTANCE, "Error while subscribing to Redis: ${e.message}")
            } finally {
                isConnected.set(false)
                isRunning.set(false)
            }
        }.apply {
            isDaemon = true
            name = "Redis-PubSub-Thread"
            start()
        }

        SkRedisLogger.info(Main.INSTANCE, "Redis PubSub thread started")
    }

    fun startPubSub() {
        operationLock.withLock {
            if (isRunning.get()) {
                SkRedisLogger.info(Main.INSTANCE, "PubSub is already running, no action taken")
                return
            }
            startPubSubInternal()
        }
    }

    fun unsubscribe() {
        operationLock.withLock {
            if (!isRunning.get()) {
                SkRedisLogger.info(Main.INSTANCE, "PubSub is not running, no action needed")
                return
            }
            shutdownInternal(false)
        }
    }

    fun restart() {
        SkRedisLogger.info(Main.INSTANCE, "Restarting Redis PubSub...")
        initialize() // Re-use initialize for consistency
    }
}