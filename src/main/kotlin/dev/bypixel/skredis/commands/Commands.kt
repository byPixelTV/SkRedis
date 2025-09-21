package dev.bypixel.skredis.commands

import ch.njol.skript.Skript
import ch.njol.skript.util.Version
import com.google.gson.Gson
import com.google.gson.JsonObject
import dev.bypixel.skredis.Main
import dev.bypixel.skredis.utils.Colors
import dev.bypixel.skredis.utils.UpdateChecker
import dev.bypixel.skredis.utils.UpdateChecker.Companion.getLatestReleaseVersion
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

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
                        "<grey>-</grey> ${Colors.MINT_GREEN_DARK.hex}$name</color> <yellow>v${addon.plugin.description.version}</yellow>"
                    } else {
                        null
                    }
                }

                val addonsList =
                    if (addonMessages.isNotEmpty()) addonMessages.joinToString("\n") else "<color:#ff0000>No other addons found</color>"
                player.sendMessage(
                    miniMessages.deserialize(
                        "<dark_grey>--- ${Colors.MINT_GREEN_DARK.hex}SkRedis</color> <grey>Info:</grey> ---</dark_grey>\n\n<grey>SkRedis Version: ${Colors.MINT_GREEN_DARK.hex}${Main.instance.description.version}</color>\nSkript Version: ${Colors.MINT_GREEN_DARK.hex}${Skript.getInstance().description.version}</color>\nServer Version: ${Colors.MINT_GREEN_DARK.hex}${Main.instance.server.minecraftVersion}</color>\nServer Implementation: ${Colors.MINT_GREEN_DARK.hex}${Bukkit.getVersion()}</color>\nAddons:\n$addonsList</grey>"
                    )
                )
            }
        }
        literalArgument("version") {
            withPermission("skredis.admin.version")
            anyExecutor { player, _ ->
                val currentVersion = Main.instance.description.version
                val updateVersion = UpdateChecker(Main.instance).getUpdateVersion(currentVersion)

                getLatestReleaseVersion { version ->
                    val plugVer = Version(Main.instance.description.version)
                    val curVer = Version(version)
                    val url = URL("https://api.github.com/repos/byPixelTV/skRedis/releases/latest")
                    val reader = BufferedReader(InputStreamReader(url.openStream()))
                    val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
                    var tagName = jsonObject["tag_name"].asString
                    tagName = tagName.removePrefix("v")
                    if (curVer <= plugVer) {
                        player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> ${Colors.GREEN.hex}The plugin is up to date!</color>"))
                    } else {
                        Main.instance.scheduler.runTaskLater({
                            updateVersion.thenApply { version ->
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> update available: ${Colors.GREEN.hex}$version</color>"))
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:blue:aqua:blue>SkRedis</gradient>]</dark_grey> download at ${Colors.MINT_GREEN_DARK.hex}<click:open_url:'https://github.com/byPixelTV/SkRedis/releases'>https://github.com/byPixelTV/SkRedis/releases</click></color>"))
                                true
                            }
                        }, 30)
                    }
                }
            }
        }
    }
}