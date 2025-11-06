package dev.bypixel.skredis

import ch.njol.skript.Skript
import ch.njol.skript.SkriptAddon
import com.github.Anon8281.universalScheduler.UniversalScheduler
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler
import dev.bypixel.lettucewrapper.LettuceRedisClient
import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.skredis.commands.Commands
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
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.axay.kspigot.main.KSpigot
import java.io.File
import java.io.IOException

class SkRedis : KSpigot() {
    private var addon: SkriptAddon? = null
    lateinit var scheduler: TaskScheduler
    lateinit var config: YamlDocument
    lateinit var lettuceClient: LettuceRedisClient

    companion object {
        lateinit var instance: SkRedis
            private set
    }

    init {
        instance = this
    }

    override fun load() {
        CommandAPI.onLoad(CommandAPIPaperConfig(this).silentLogs(true).verboseOutput(true).setNamespace("skredis"))
        Commands()
    }

    override fun startup() {
        instance = this

        scheduler = UniversalScheduler.getScheduler(this)

        CommandAPI.onEnable()
        this.addon = Skript.registerAddon(this)
        val localAddon = this.addon

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

        lettuceClient = LettuceRedisClient(
            redisHost, redisPort, redisPassword,
            CoroutineScope(Dispatchers.IO), redisUser, redisDatabase
        )

        RedisListener.setLettuceClient(lettuceClient)

        SkRedisCoroutineScope.launch(Dispatchers.IO) {
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

        RedisListenerImpl

        try {
            localAddon?.loadClasses("dev.bypixel.skredis.skript", "elements")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        PlayerJoinListener()

        SkRedisLogger.success("SkRedis has been successfully enabled!")
    }

    override fun shutdown() {
        RedisListener.unregisterListener(RedisListenerImpl)
        CommandAPI.onDisable()

        runBlocking {
            UpdateUtil.updateJob.cancelAndJoin()
            lettuceClient.close()
        }
    }
}