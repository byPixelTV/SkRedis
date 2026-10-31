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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

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
            val classLoader = javaClass.classLoader

            ClassGraph()
                .enableClassLoaders(classLoader)
                .enableClasspath()
                .acceptPackages("dev.bypixel.skredis.skript.elements")
                .enableClassInfo()
                .scan()
                .use { scan ->
                    val elements = scan.allClasses.filter { info ->
                        info.isPublic && !info.isAbstract && !info.isNestedClass
                    }

                    SkRedisLogger.info("Found ${elements.size} Skript elements, registering...")
                    val startMillis = System.currentTimeMillis()

                    var success = 0
                    var failed = 0
                    val packagesList = mutableListOf<String>()

                    elements.forEach { info ->
                        runCatching {
                            val clazz = Class.forName(info.name, false, classLoader)
                            registerSkriptElement(clazz)
                        }.onSuccess {
                            packagesList.add(info.packageName)
                            success++
                        }.onFailure { exception ->
                            failed++
                            SkRedisLogger.error("Failed to register Skript element: ${info.name}")
                            ((exception as? InvocationTargetException)?.targetException ?: exception).printStackTrace()
                        }
                    }

                    val endMillis = System.currentTimeMillis()
                    SkRedisLogger.success("Registered $success/${elements.size} Skript elements in ${endMillis - startMillis} ms.")

                    if (failed > 0) {
                        SkRedisLogger.warn("Failed to register $failed Skript elements.")
                    }

                    if (success > 0) {
                        val packageCounts = packagesList.groupingBy { it }.eachCount()
                        SkRedisLogger.info("Registered elements by package:")
                        packageCounts.forEach { (pkg, count) ->
                            SkRedisLogger.info(" - ${pkg.substringAfterLast(".")}: $count")
                        }
                    }
                }
        } catch (exception: Exception) {
            SkRedisLogger.error("Failed to scan Skript elements: ${exception.message}")
            exception.printStackTrace()
        }

        PlayerJoinListener()

        SkRedisLogger.success("SkRedis has been successfully enabled!")
    }

    private fun registerSkriptElement(clazz: Class<*>) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == "register" && it.parameterCount == 0
        }

        if (method != null) {
            val target = if (Modifier.isStatic(method.modifiers)) {
                null
            } else {
                val instanceField = clazz.declaredFields.firstOrNull {
                    it.name == "INSTANCE" && Modifier.isStatic(it.modifiers)
                }

                if (instanceField != null) {
                    instanceField.isAccessible = true
                    instanceField.get(null)
                } else {
                    clazz.getDeclaredConstructor().apply {
                        isAccessible = true
                    }.newInstance()
                }
            }

            method.isAccessible = true
            method.invoke(target)
            return
        }

        val companionField = clazz.declaredFields.firstOrNull {
            it.name == "Companion" && Modifier.isStatic(it.modifiers)
        } ?: throw NoSuchMethodException("No parameterless register() in ${clazz.name} or its companion")

        companionField.isAccessible = true
        val companion = companionField.get(null)
            ?: throw IllegalStateException("Companion is null for ${clazz.name}")

        val companionMethod = companion.javaClass.declaredMethods.firstOrNull {
            it.name == "register" && it.parameterCount == 0
        } ?: throw NoSuchMethodException("No parameterless register() in ${clazz.name} or its companion")

        companionMethod.isAccessible = true
        companionMethod.invoke(if (Modifier.isStatic(companionMethod.modifiers)) null else companion)
    }

    override fun shutdown() {
        RedisListener.unregisterListener(RedisListenerImpl)

        runBlocking {
            UpdateUtil.updateJob.cancelAndJoin()
            lettuceClient.close()
        }
    }
}