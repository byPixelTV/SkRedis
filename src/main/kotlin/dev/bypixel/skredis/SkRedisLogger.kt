package dev.bypixel.skredis

import dev.bypixel.skredis.utils.Colors
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin

object SkRedisLogger {
    private val mm = MiniMessage.miniMessage()

    fun getCleanCallingClassName(): String {
        val stackTrace = Thread.currentThread().stackTrace

        // Find the first meaningful caller class
        for (i in 3 until stackTrace.size) { // Start from index 3 to skip the current method and the caller method
            val className = stackTrace[i].className
            if (!className.startsWith("java.") &&
                !className.startsWith("com.velocitypowered.") &&
                !className.startsWith("io.papermc.") &&
                !className.startsWith("org.bukkit.") &&
                !className.startsWith("org.spigot.") &&
                !className.startsWith("net.minecraft.server.") &&
                !className.contains("$$") && // Remove dynamically generated classes
                !className.contains("Lambda")) {

                // Return a clean simple class name
                return className.substring(className.lastIndexOf('.') + 1)
            }
        }

        return "UnknownSource" // Fallback if no valid caller is found
    }

    fun info(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.GREY.hex}${Colors.GREEN.hex}[INFO]</color> ${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }

    fun error(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.RED.hex}[ERROR] ${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }

    fun warn(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.ORANGE.hex}[WARN] ${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }

    fun debug(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.PURPLE.hex}[DEBUG] ${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }

    fun success(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.GREEN.hex}[SUCCESS] ${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }

    fun consoleMessage(plugin: Plugin, message: String) {
        val className = getCleanCallingClassName()

        plugin.server.consoleSender.sendMessage(mm.deserialize("${Colors.GREY.hex}${Colors.BLUE.hex}[${plugin.name}]</color> ${Colors.YELLOW.hex}[$className]</color> $message</color>"))
    }
}