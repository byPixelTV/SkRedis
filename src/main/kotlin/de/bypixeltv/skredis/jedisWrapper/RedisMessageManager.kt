package de.bypixeltv.skredis.jedisWrapper

import de.bypixeltv.skredis.Main
import de.bypixeltv.skredis.config.ConfigLoader
import redis.clients.jedis.Jedis
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

            // Stop the thread first
            val thread = pubSubThread
            if (thread != null) {
                if (isSystemShutdown) {
                    Main.INSTANCE.sendInfoLogs("Shutting down Redis PubSub thread (system shutdown)")
                } else {
                    Main.INSTANCE.sendInfoLogs("Shutting down Redis PubSub thread")
                }

                thread.interrupt()
                try {
                    thread.join(3000) // Give it a reasonable time to finish
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            // Now try to unsubscribe if needed
            if (currentPubSub != null && isConnected.get()) {
                try {
                    currentPubSub.punsubscribe()
                    Main.INSTANCE.sendInfoLogs("Redis PubSub unsubscribe sent")
                } catch (e: Exception) {
                    if (!isSystemShutdown) {
                        Main.INSTANCE.sendErrorLogs("Error unsubscribing: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            if (!isSystemShutdown) {
                Main.INSTANCE.sendErrorLogs("Error during shutdown: ${e.message}")
            }
        } finally {
            isRunning.set(false)
            isConnected.set(false)
            pubSubThread = null
        }
    }

    fun shutdown() {
        operationLock.withLock {
            shutdownInternal(false)
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
                Main.INSTANCE.sendInfoLogs("Subscribed to pattern: $pattern ($subscribedChannels channels)")
            }

            override fun onPUnsubscribe(pattern: String, subscribedChannels: Int) {
                if (subscribedChannels == 0) {
                    isConnected.set(false)
                    Main.INSTANCE.sendInfoLogs("Unsubscribed from all channels")
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
                    Main.INSTANCE.sendInfoLogs("Starting Redis PubSub subscription")
                    jedis.psubscribe(newPubSub, "*")
                }
            } catch (e: JedisConnectionException) {
                Main.INSTANCE.sendErrorLogs("Redis connection error: ${e.message}")
            } catch (e: Exception) {
                Main.INSTANCE.sendErrorLogs("Error while subscribing to Redis: ${e.message}")
            } finally {
                isConnected.set(false)
                isRunning.set(false)
            }
        }.apply {
            isDaemon = true
            name = "Redis-PubSub-Thread"
            start()
        }

        Main.INSTANCE.sendInfoLogs("Redis PubSub thread started")
    }

    fun startPubSub() {
        operationLock.withLock {
            if (isRunning.get()) {
                Main.INSTANCE.sendInfoLogs("PubSub is already running, no action taken")
                return
            }
            startPubSubInternal()
        }
    }

    fun unsubscribe() {
        operationLock.withLock {
            if (!isRunning.get()) {
                Main.INSTANCE.sendInfoLogs("PubSub is not running, no action needed")
                return
            }
            shutdownInternal(false)
        }
    }

    fun restart() {
        Main.INSTANCE.sendInfoLogs("Restarting Redis PubSub...")
        initialize() // Re-use initialize for consistency
    }
}