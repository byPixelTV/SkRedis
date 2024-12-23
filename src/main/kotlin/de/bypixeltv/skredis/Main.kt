package de.bypixeltv.skredis

import ch.njol.skript.Skript
import ch.njol.skript.SkriptAddon
import de.bypixeltv.skredis.commands.Commands
import de.bypixeltv.skredis.managers.RedisController
import de.bypixeltv.skredis.managers.RedisMessageManager
import de.bypixeltv.skredis.utils.IngameUpdateChecker
import de.bypixeltv.skredis.utils.UpdateChecker
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import net.axay.kspigot.main.KSpigot
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import java.io.IOException

class Main : KSpigot() {

    private val miniMessages = MiniMessage.miniMessage()
    private var redisController: RedisController? = null

    private var adventure: BukkitAudiences? = null

    fun sendLogs(message: String) {
        this.adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<grey>[<aqua>SkRedis</aqua>]</grey> <yellow>$message</yellow>"))
    }

    fun sendInfoLogs(message: String) {
        this.adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<grey>[<aqua>SkRedis</aqua>]</grey> <green>$message</green>"))
    }

    fun sendErrorLogs(message: String) {
        this.adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<grey>[<aqua>SkRedis</aqua>]</grey> <red>$message</red>"))
    }

    fun getRC(): RedisController? {
        return redisController
    }

    fun getAdventure(): BukkitAudiences? {
        return adventure
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
        saveDefaultConfig()
        CommandAPI.onEnable()

        INSTANCE = this
        this.instance = this
        this.addon = Skript.registerAddon(this)
        val localAddon = this.addon

        redisController = RedisController(this)

        adventure = BukkitAudiences.create(this)

        IngameUpdateChecker

        val version = description.version
        if (version.contains("-")) {
            adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<yellow>This is a BETA build, things may not work as expected, please report any bugs on GitHub</yellow>"))
            adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<yellow>This is a BETA build, things may not work as expected, please report any bugs on GitHub</yellow>"))
            adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<yellow>https://github.com/byPixelTV/SkRedis/issues</yellow>"))
        }

        UpdateChecker.checkForUpdate(version)
        RedisMessageManager

        adventure?.server(server.name)?.sendMessage(miniMessages.deserialize("<gray>[<aqua>SkRedis</aqua>]</gray> <yellow>Successfully enabled SkRedis!</yellow>"))

        try {
            localAddon?.loadClasses("de.bypixeltv.skredis.skript", "elements")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun shutdown() {
        if (redisController != null) {
            redisController!!.shutdown()
        }
        if (adventure != null) {
            adventure!!.close()
            this.adventure = null
        }
    }
}