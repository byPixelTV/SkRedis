package dev.bypixel.skredis

import ch.njol.skript.Skript
import ch.njol.skript.SkriptAddon
import com.github.Anon8281.universalScheduler.UniversalScheduler
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler
import dev.bypixel.skredis.commands.Commands
import dev.bypixel.skredis.config.ConfigLoader
import dev.bypixel.skredis.lettuce.LettuceRedisClient
import dev.bypixel.skredis.lettuce.RedisListenerImpl
import dev.bypixel.skredis.lettuce.listener.RedisListener
import dev.bypixel.skredis.utils.IngameUpdateChecker
import dev.bypixel.skredis.utils.SkRedisCoroutineScope
import dev.bypixel.skredis.utils.UpdateChecker
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.axay.kspigot.main.KSpigot
import java.io.IOException

class Main : KSpigot() {
    private var addon: SkriptAddon? = null
    lateinit var scheduler: TaskScheduler

    companion object {
        lateinit var instance: Main
            private set
    }

    init {
        instance = this
    }

    override fun load() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true).verboseOutput(true).setNamespace("skredis"))
        Commands()
    }

    override fun startup() {
        instance = this

        ConfigLoader

        CommandAPI.onEnable()
        this.addon = Skript.registerAddon(this)
        val localAddon = this.addon

        RedisListenerImpl

        IngameUpdateChecker

        scheduler = UniversalScheduler.getScheduler(this)

        val version = pluginMeta.version
        if (version.contains("-")) {
            SkRedisLogger.info(this, "This is a BETA build, things may not work as expected, please report any bugs on GitHub")
            SkRedisLogger.info(this, "<yellow>https://github.com/byPixelTV/SkRedis/issues</yellow>")
        }

        UpdateChecker.checkForUpdate(version)

        SkRedisLogger.success(this, "SkRedis has been successfully enabled!")

        try {
            localAddon?.loadClasses("dev.bypixel.skredis.skript", "elements")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            if (LettuceRedisClient.tryConnection()) {
                SkRedisLogger.success(instance, "Successfully connected to the Redis server.")
            } else {
                SkRedisLogger.error(instance, "Could not connect to the Redis server, please check your configuration. Disabling...")
                server.pluginManager.disablePlugin(instance)
            }
        }
    }

    override fun shutdown() {
        RedisListener.unregisterListener(RedisListenerImpl)
    }
}