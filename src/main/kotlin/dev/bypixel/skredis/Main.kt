package dev.bypixel.skredis

import ch.njol.skript.Skript
import ch.njol.skript.SkriptAddon
import dev.bypixel.skredis.commands.Commands
import dev.bypixel.skredis.config.ConfigLoader
import dev.bypixel.skredis.jedisWrapper.RedisController
import dev.bypixel.skredis.jedisWrapper.RedisMessageManager
import dev.bypixel.skredis.utils.IngameUpdateChecker
import dev.bypixel.skredis.utils.UpdateChecker
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import net.axay.kspigot.main.KSpigot
import java.io.IOException

class Main : KSpigot() {

    private var redisController: RedisController? = null

    fun getRC(): RedisController? {
        return redisController
    }

    fun setRedisController() {
        redisController = RedisController(this)
    }

    private var instance: Main? = null
    private var addon: SkriptAddon? = null

    companion object {
        lateinit var INSTANCE: Main
    }

    init {
        instance = this
    }

    override fun load() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true).verboseOutput(true).setNamespace("skredis"))
        Commands()
    }

    @Suppress("DEPRECATION")
    override fun startup() {
        INSTANCE = this
        this.instance = this

        ConfigLoader

        CommandAPI.onEnable()
        this.addon = Skript.registerAddon(this)
        val localAddon = this.addon

        redisController = RedisController(this)

        IngameUpdateChecker

        val version = description.version
        if (version.contains("-")) {
            SkRedisLogger.info(this, "This is a BETA build, things may not work as expected, please report any bugs on GitHub")
            SkRedisLogger.info(this, "<yellow>https://github.com/byPixelTV/SkRedis/issues</yellow>")
        }

        UpdateChecker.checkForUpdate(version)
        RedisMessageManager.initialize()

        SkRedisLogger.success(this, "SkRedis has been successfully enabled!")

        try {
            localAddon?.loadClasses("dev.bypixel.skredis.skript", "elements")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun shutdown() {
        RedisMessageManager.unsubscribe()
        if (redisController != null) {
            redisController!!.shutdown()
        }
    }
}