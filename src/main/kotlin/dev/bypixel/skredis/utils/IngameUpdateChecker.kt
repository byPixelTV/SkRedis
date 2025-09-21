package dev.bypixel.skredis.utils

import dev.bypixel.skredis.Main
import net.axay.kspigot.event.listen
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent

object IngameUpdateChecker {
    private val miniMessages = MiniMessage.miniMessage()

    @Suppress("DEPRECATION", "UNUSED")
    val joinEvent = listen<PlayerJoinEvent> {
        val player = it.player
        if (Main.instance.config.getBoolean("update-checker")) {
            if (player.hasPermission("skredis.admin.version") || player.isOp) {
                val currentVersion = Main.instance.description.version
                val updateVersion = UpdateChecker(Main.instance).getUpdateVersion(currentVersion)

                Main.instance.scheduler.runTaskLater({
                    updateVersion.thenApply { version ->
                        player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> update available: <green>$version</green>"))
                        player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> download at <aqua><click:open_url:'https://github.com/byPixelTV/skRedis/releases'>https://github.com/byPixelTV/skRedis/releases</click></aqua>"))
                        true
                    }
                }, 30)
            }
        }
    }
}