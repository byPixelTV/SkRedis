package dev.bypixel.skredis.events

import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.SkRedisCoroutineScope
import dev.bypixel.skredis.utils.update.UpdateUtil
import dev.bypixel.skredis.utils.update.Version
import dev.dejvokep.boostedyaml.route.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.axay.kspigot.event.listen
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener {
    init {
        listen<PlayerJoinEvent> {
            val player = it.player

            if (SkRedis.instance.config.getBoolean(Route.fromString("update-check.enabled")) && SkRedis.instance.config.getBoolean(Route.fromString("update-check.notify-admins"))) {
                if (player.hasPermission("skredis.admin.updatecheck")) {
                    val cachedVersion = UpdateUtil.getLatestCachedVersion()
                    if (cachedVersion != null) {
                        SkRedisCoroutineScope.launch(Dispatchers.IO) {
                            val currentVersionString = SkRedis.instance.pluginMeta.version
                            val latestVersion = Version.fromString(cachedVersion)
                            val currentVersion = Version.fromString(currentVersionString)
                            val compare = latestVersion.compareTo(currentVersion)

                            if (currentVersionString.contains("+")) {
                                return@launch
                            }

                            if (compare > 0) {
                                delay(2000L) // Delay to ensure the player has fully logged in
                                player.sendMessage(
                                    MiniMessage.miniMessage().deserialize(
                                        "<prefix> An <#08a8f8>update</#08a8f8> is available! You are running version <#dc2626><current_version></#dc2626>, latest version is <#4bfb00><latest_version></#4bfb00>. Download it on <click:open_url:'https://www.github.com/byPixelTV/SkRedis/releases'><u><#08a8f8>GitHub (click)</#08a8f8></u></click>.",
                                        Placeholder.unparsed("current_version", currentVersionString),
                                        Placeholder.unparsed("latest_version", cachedVersion),
                                        Placeholder.parsed("prefix", "<#08a8f8>[SkRedis]</#08a8f8>")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}