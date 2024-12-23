package de.bypixeltv.skredis.commands

import ch.njol.skript.Skript
import ch.njol.skript.util.Version
import com.google.gson.Gson
import com.google.gson.JsonObject
import de.bypixeltv.skredis.Main
import de.bypixeltv.skredis.managers.RedisMessageManager
import de.bypixeltv.skredis.utils.UpdateChecker
import de.bypixeltv.skredis.utils.UpdateChecker.Companion.getLatestReleaseVersion
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

class Commands {
    private val miniMessages = MiniMessage.miniMessage()

    @Suppress("UNUSED", "DEPRECATION")
    val command = commandTree("skredis") {
        withPermission("skredis.admin")
        literalArgument("info") {
            withPermission("skredis.admin.info")
            anyExecutor { player, _ ->
                val addonMessages = Skript.getAddons().mapNotNull { addon ->
                    val name = addon.name
                    if (!name.contains("SkRedis")) {
                        "<grey>-</grey> <aqua>$name</aqua> <yellow>v${addon.plugin.description.version}</yellow>"
                    } else {
                        null
                    }
                }

                val addonsList =
                    if (addonMessages.isNotEmpty()) addonMessages.joinToString("\n") else "<color:#ff0000>No other addons found</color>"
                player.sendMessage(
                    miniMessages.deserialize(
                        "<dark_grey>--- <aqua>SkRedis</aqua> <grey>Info:</grey> ---</dark_grey>\n\n<grey>SkRedis Version: <aqua>${Main.INSTANCE.description.version}</aqua>\nSkript Version: <aqua>${Skript.getInstance().description.version}</aqua>\nServer Version: <aqua>${Main.INSTANCE.server.minecraftVersion}</aqua>\nServer Implementation: <aqua>${Main.INSTANCE.server.version}</aqua>\nAddons:\n$addonsList</grey>"
                    )
                )
            }
        }
        literalArgument("version") {
            withPermission("skredis.admin.version")
            anyExecutor { player, _ ->
                val currentVersion = Main.INSTANCE.description.version
                val updateVersion = UpdateChecker(Main.INSTANCE).getUpdateVersion(currentVersion)

                getLatestReleaseVersion { version ->
                    val plugVer = Version(Main.INSTANCE.description.version)
                    val curVer = Version(version)
                    val url = URL("https://api.github.com/repos/byPixelTV/skRedis/releases/latest")
                    val reader = BufferedReader(InputStreamReader(url.openStream()))
                    val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
                    var tagName = jsonObject["tag_name"].asString
                    tagName = tagName.removePrefix("v")
                    if (curVer <= plugVer) {
                        player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> <green>The plugin is up to date!</green>"))
                    } else {
                        Bukkit.getScheduler().runTaskLater(Main.INSTANCE, Runnable {
                            updateVersion.thenApply { version ->
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> update available: <green>$version</green>"))
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> download at <aqua><click:open_url:'https://github.com/byPixelTV/SkRedis/releases'>https://github.com/byPixelTV/SkRedis/releases</click></aqua>"))
                                true
                            }
                        }, 30)
                    }
                }
            }
        }
        literalArgument("reload") {
            withPermission("skredis.admin.reload")
            anyExecutor { player, _ ->
                Main.INSTANCE.reloadConfig()
                val path = Paths.get("/plugins/SkRedis/config.yml")
                if (Files.exists(path)) {
                    Main.INSTANCE.saveConfig()
                } else {
                    Main.INSTANCE.saveDefaultConfig()
                }
                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> <color:#43fa00>Successfully reloaded the config!</color>"))
            }
        }
        literalArgument("reloadredis") {
            withPermission("skredis.admin.reloadredis")
            anyExecutor { player, _ ->
                Main.INSTANCE.reloadConfig()
                val path = Paths.get("/plugins/SkRedis/config.yml")
                if (Files.exists(path)) {
                    Main.INSTANCE.saveConfig()
                } else {
                    Main.INSTANCE.saveDefaultConfig()
                }
                try {
                    RedisMessageManager.reloadRedisConnection()
                    player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> <color:#43fa00>Successfully reloaded the redis connection!</color>"))
                } catch (e: Exception) {
                    player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> <red>Failed to reload the Redis connection!</red>"))
                    e.printStackTrace()
                    return@anyExecutor
                }
            }
        }
    }
}