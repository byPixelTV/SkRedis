package dev.bypixel.skredis

import ch.njol.skript.Skript
import com.github.Anon8281.universalScheduler.UniversalScheduler
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler
import dev.bypixel.lettucewrapper.LettuceRedisClient
import dev.bypixel.lettucewrapper.RedisCredentials
import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.skredis.events.PlayerJoinListener
import dev.bypixel.skredis.pubsub.RedisListenerImpl
import dev.bypixel.skredis.utils.update.UpdateUtil
import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning
import dev.dejvokep.boostedyaml.route.Route
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import net.axay.kspigot.main.KSpigot
import org.bxteam.quark.paper.PaperLibraryManager
import org.skriptlang.skript.addon.SkriptAddon
import java.io.File
import java.io.IOException

class SkRedis : KSpigot() {
    lateinit var addon: SkriptAddon
    lateinit var scheduler: TaskScheduler
    lateinit var config: YamlDocument
    lateinit var lettuceClient: LettuceRedisClient
    lateinit var libraryManager: PaperLibraryManager

    companion object {
        lateinit var instance: SkRedis
            private set
    }

    init {
        System.setProperty("io.lettuce.core.epoll", "false")
        System.setProperty("io.lettuce.core.iouring", "false")
        System.setProperty("io.lettuce.core.kqueue", "false")
        instance = this
    }

    override fun startup() {
        instance = this

        libraryManager = PaperLibraryManager(this)
        libraryManager.loadFromGradle()

        scheduler = UniversalScheduler.getScheduler(this)

        addon = Skript.instance().registerAddon(this::class.java, "SkRedis")

        val serverConfigFile = File("plugins/SkRedis/config.yml")

        if (serverConfigFile.exists()) {
            val content = serverConfigFile.readText()
            if (content.contains("configVersion: 1")) {
                serverConfigFile.deleteRecursively()
                SkRedisLogger.warn("Old configuration file detected and deleted. A new configuration file has been generated.")
            }
        }

        val configInputStream = object {}.javaClass.getResourceAsStream("/config.yml")
        config = YamlDocument.create(
            File("plugins/SkRedis/config.yml"), configInputStream!!, GeneralSettings.builder().setKeyFormat(
            GeneralSettings.KeyFormat.OBJECT).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(
            BasicVersioning("config-version")
        ).build())

        val redisHost = config.getString(Route.fromString("redis.host"))
        val redisPort = config.getInt(Route.fromString("redis.port"))
        val redisPassword = config.getString(Route.fromString("redis.password"), null)
        val redisDatabase = config.getInt(Route.fromString("redis.database"), 0)
        val redisUser = config.getString(Route.fromString("redis.username"), null)
        val redisSsl = config.getBoolean(Route.fromString("redis.ssl"), false)
        val redisConnectionTimeout = config.getLong(Route.fromString("redis.connectionTimeout"), 2000L)
        val redisConnectionPoolSize = config.getInt(Route.fromString("redis.connectionPoolSize"), 10)
        val redisAllowSelfSignedCertificates = config.getBoolean(Route.fromString("redis.allowSelfSignedCertificates"), false)
        val redisTrustStorePath = config.getString(Route.fromString("redis.trustStorePath"), null)
        val redisTrustStorePassword = config.getString(Route.fromString("redis.trustStorePassword"), null)

        try {
            lettuceClient = if (!redisSsl) {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        timeoutMillis = redisConnectionTimeout,
                    ), SkRedisCoroutineScope, redisConnectionPoolSize)
            } else if ((redisTrustStorePath == null || redisTrustStorePassword == null) && redisSsl) {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        true,
                        redisAllowSelfSignedCertificates,
                        timeoutMillis = redisConnectionTimeout
                    ), SkRedisCoroutineScope, redisConnectionPoolSize)
            } else {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        true,
                        redisAllowSelfSignedCertificates,
                        timeoutMillis = redisConnectionTimeout,
                        trustStorePath = redisTrustStorePath,
                        trustStorePassword = redisTrustStorePassword
                    ), SkRedisCoroutineScope, redisConnectionPoolSize)
            }
        } catch (e: Exception) {
            SkRedisLogger.error("Could not connect to the Redis server, please check your configuration. Disabling...<br>${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(instance)
            return
        }

        runBlocking {
            if (lettuceClient.connection.isOpen) {
                SkRedisLogger.success("Successfully connected to the Redis server.")
            } else {
                SkRedisLogger.error("Could not connect to the Redis server, please check your configuration. Disabling...")
                server.pluginManager.disablePlugin(instance)
            }

            if (config.getBoolean(Route.fromString("update-check.enabled"))) {
                UpdateUtil.updateJob.start()
            }
        }

        RedisListener.setLettuceClient(lettuceClient)

        RedisListenerImpl

        try {
            val scan = ClassGraph()
                .acceptPackages("dev.bypixel.skredis.skript.elements")
                .enableClassInfo()
                .scan()

            SkRedisLogger.info("Found ${scan.allClasses.size} Skript elements, registering...")
            val startMillis = System.currentTimeMillis()

            var classCount = 0
            var success = 0
            var failed = 0

            val packagesList = mutableListOf<String>()

            scan.allClasses
                .filter { it.isPublic && !it.isAbstract && !it.isInnerClass }
                .forEach { info ->
                    runCatching {
                        val clazz = info.loadClass()


                        fun findRegisterIn(c: Class<*>?): java.lang.reflect.Method? {
                            if (c == null) return null
                            return c.declaredMethods.firstOrNull { it.name == "register" && it.parameterCount == 0 }
                        }

                        var method = findRegisterIn(clazz)
                        var target: Any? = null

                        if (method == null) {
                            val instanceField = clazz.declaredFields.firstOrNull { it.name == "INSTANCE" }
                            if (instanceField != null) {
                                instanceField.isAccessible = true
                                val instance = instanceField.get(null)
                                method = findRegisterIn(instance?.javaClass)
                                if (method != null) target = instance
                            }
                        }

                        if (method == null) {
                            val companionField = clazz.declaredFields.firstOrNull { it.name == "Companion" }
                            if (companionField != null) {
                                companionField.isAccessible = true
                                val companion = companionField.get(null)
                                method = findRegisterIn(companion?.javaClass)
                                if (method != null) target = companion
                            }
                        }

                        if (method == null) {
                            method = clazz.declaredMethods.firstOrNull { it.name == "register" && it.parameterCount == 0 }
                        }

                        method = method ?: throw NoSuchMethodException("No parameterless register() in ${clazz.name} or its companion/object")

                        method.isAccessible = true

                        if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                            method.invoke(null)
                        } else {
                            if (target == null) {
                                val ctor = try { clazz.getDeclaredConstructor() } catch (_: Exception) { null }
                                if (ctor != null) {
                                    ctor.isAccessible = true
                                    target = ctor.newInstance()
                                }
                            }

                            val declaring = method.declaringClass
                            if (target == null || !declaring.isInstance(target)) {
                                throw IllegalStateException("Could not obtain suitable instance of ${declaring.name} to invoke register() for ${clazz.name}")
                            }

                            method.invoke(target)
                        }
                    }.onSuccess {
                        packagesList.add(info.packageName)
                        success++
                    }.onFailure { ex ->
                        failed++
                        SkRedisLogger.error("Failed to register Skript element: ${info.name}")
                        ex.printStackTrace()
                    }
                    classCount++
                }

            val endMillis = System.currentTimeMillis()
            SkRedisLogger.success("Registered $success/$classCount Skript elements in ${endMillis - startMillis} ms.")
            if (success > 0) {
                // print packages with class count
                val packageCounts = packagesList.groupingBy { it }.eachCount()
                SkRedisLogger.info("Registered elements by package:")
                packageCounts.forEach { (pkg, count) ->
                    SkRedisLogger.info(" - ${pkg.substringAfterLast(".")}: $count")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        PlayerJoinListener()

        SkRedisLogger.success("SkRedis has been successfully enabled!")
    }

    override fun shutdown() {
        RedisListener.unregisterListener(RedisListenerImpl)

        runBlocking {
            UpdateUtil.updateJob.cancelAndJoin()
            lettuceClient.close()
        }
    }
}