package dev.bypixel.skredis.commands

import ch.njol.skript.Skript
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.utils.Colors
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit

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

                val serverSoftware = Bukkit.getServer().name

                val addonsList =
                    if (addonMessages.isNotEmpty()) addonMessages.joinToString("\n") else "<color:#ff0000>No other addons found</color>"
                player.sendMessage(
                    miniMessages.deserialize(
                        "<dark_grey>--- ${Colors.MINT_GREEN_DARK.hex}SkRedis</color> <grey>Info:</grey> ---</dark_grey>\n\n<grey>SkRedis Version: ${Colors.MINT_GREEN_DARK.hex}${SkRedis.instance.description.version}</color>\nSkript Version: ${Colors.MINT_GREEN_DARK.hex}${Skript.getInstance().description.version}</color>\nServer Version: ${Colors.MINT_GREEN_DARK.hex}${SkRedis.instance.server.minecraftVersion}</color>\nServer Implementation: ${Colors.MINT_GREEN_DARK.hex}$serverSoftware ${Bukkit.getVersion()}</color>\nAddons:\n$addonsList</grey>"
                    )
                )
            }
        }
    }
}