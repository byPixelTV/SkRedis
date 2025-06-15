package dev.bypixel.skredis.config

import dev.bypixel.skredis.Main
import dev.bypixel.skredis.SkRedisLogger
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigLoader {
    private val loaderOptions = LoaderOptions()
    private val constructor = Constructor(Config::class.java, loaderOptions).apply {
        propertyUtils = LenientPropertyUtils().apply { isSkipMissingProperties = true }
    }
    private val yaml = Yaml(constructor)

    var config: Config? = null
        private set

    private var configNeedsSaving = false

    init {
        load()
    }

    fun load() {
        val configFile = File("plugins/SkRedis/config.yml")
        configNeedsSaving = false

        // Create directories if needed
        configFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) {
                SkRedisLogger.error(Main.INSTANCE, "Failed to create configuration directory at ${it.absolutePath}")
            }
        } ?: return

        // If no config exists, create default
        if (!configFile.exists()) {
            SkRedisLogger.warn(Main.INSTANCE, "Configuration file not found! Creating a new one...")
            config = Config()
            configNeedsSaving = true
            save()
            return
        }

        try {
            FileInputStream(configFile).use { inputStream ->
                // Load the configuration as a Map to capture all keys including unknown ones.
                val taglessYaml = Yaml()
                val loadedMap = taglessYaml.load<MutableMap<String, Any>>(inputStream)

                // Dump the default configuration into a map.
                val defaultConfig = Config()
                val defaultString = taglessYaml.dumpAsMap(defaultConfig)
                val defaultMap = taglessYaml.load<Map<String, Any>>(defaultString)

                // Merge the loaded map with the default map.
                val changed = mergeRecursive(defaultMap, loadedMap, "root")

                // Convert the merged map back to a string and then to a Config instance.
                val mergedString = taglessYaml.dump(loadedMap)
                config = yaml.loadAs(mergedString, Config::class.java)

                if (changed) {
                    configNeedsSaving = true
                    save()
                }
            }
        } catch (e: Exception) {
            SkRedisLogger.error(Main.INSTANCE, "Failed to load configuration file: ${e.message}")
            if (configFile.exists() && configFile.length() > 0) {
                backupConfig(configFile)
            }
            config = Config()
            configNeedsSaving = true
            save()
        }
    }

    fun save() {
        if (!configNeedsSaving && config != null) {
            SkRedisLogger.info(Main.INSTANCE, "No changes to save in the configuration file.")
            return
        }

        val configFile = File("plugins/SkRedis/config.yml")
        try {
            FileWriter(configFile).use { writer ->
                config?.let { config ->
                    writer.write("# This is the internal version of the config, DO NOT MODIFY THIS VALUE\n")
                    writer.write("configVersion: ${config.configVersion}\n\n")
                    writer.write("# whether players with the permission 'skredis.admin.version' should be notified if there is an update.\n")
                    writer.write("updateChecker: ${config.updateChecker}\n\n")

                    writer.write("# Redis configuration\n")
                    writer.write("redis:\n")
                    with(config.redis) {
                        writer.write("  # The hostname of your redis server, you can use free redis hosting (search for it online) if you do not have the ability to host your own redis server. (https://redis.io/)\n  # A redis server is very lightweight, takes under 30 MB of RAM usually\n")
                        writer.write("  host: $host\n")
                        writer.write("  port: $port\n")
                        writer.write("  # The username of your redis server, if you do not have a username, leave it as default\n")
                        writer.write("  username: $username\n")
                        writer.write("  # A secure password that cannot be cracked, please change it!\n  # It is also recommended to firewall your redis server with iptables or another firewall, like ufw, so it can only be accessed by specific IP addresses\n")
                        writer.write("  password: $password\n")
                        writer.write("  # Only use this if you're running Redis 6.0.6 or higher, older versions will not work correctly\n  # It encrypts your traffic and makes data exchange between distant servers secure\n")
                        writer.write("  useSsl: $useSsl\n")
                        writer.write("  # Must be 2 or higher, if you set to lower, the addon will automatically use 2 as a minimum\n  # Do not edit MaxConnections if you do not know what you're doing\n  # It is only useful to increase this number to account for PING between distant servers and when you are sending a lot of messages constantly\n")
                        writer.write("  maxConnections: $maxConnections\n")
                        writer.write("  # The timeout of the connection in milliseconds, if you have a slow connection, increase this number\n")
                        writer.write("  timeout: $timeout\n")
                        writer.write("  # If you want to use a custom message format, set this to true\n  # If you do not know what you're doing, leave this as false\n")
                        writer.write("  useCustomMessageFormat: $useCustomMessageFormat\n")
                        writer.write("  # The format of the messages that SkRedis will sent, you can change this if you know what you're doing\n  # %message% will be replaced with the message you send. If the format is not a valid json it will just return the plain string with the replaced placeholders you entered here.\n")
                        writer.write("  messageFormat: |\n")
                        writer.write("    $messageFormat\n\n")
                    }
                    writer.write("# Enable support for RediVelocity, a modern Velocity plugin similar to RedisBungee.\n# It is recommended to use RediVelocity instead of RedisBungee, and also we only support RediVelocity, but it is no problem to have both plugins on your proxy\n# Ths will return the Redis message as JSON string, I recommend SkJson for JSON in Skript\n")
                    writer.write("redivelocity:\n")
                    with(config.redivelocity) {
                        writer.write("  enabled: $enabled\n")
                    }
                    writer.write("# The channels that SkRedis will listen to, you can add more channels if you want to\n# You can always send messages to all channels.\n# The ideal setup is having one global channel and having one channel that represents the server name, a group of servers or a specific system, so you know who to send messages to and to keep it clean, but you can do whatever you want here!\n")
                    writer.write("channels:\n")
                    config.channels.forEach { writer.write("  - $it\n") }
                }
            }
            SkRedisLogger.success(Main.INSTANCE, "Configuration saved successfully at ${configFile.absolutePath}")
            configNeedsSaving = false
        } catch (e: Exception) {
            SkRedisLogger.error(Main.INSTANCE, "Failed to save configuration file: ${e.message}")
        }
    }

    fun reload() {
        SkRedisLogger.info(Main.INSTANCE, "Reloading configuration...")
        config?.configVersion ?: -1
        load()
        SkRedisLogger.success(Main.INSTANCE, "Configuration reloaded successfully!")
    }

    private fun backupConfig(configFile: File) {
        try {
            val backupDir = File(configFile.parentFile, "config-backup")
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                SkRedisLogger.error(Main.INSTANCE, "Failed to create backup directory at ${backupDir.absolutePath}")
                return
            }
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "${configFile.nameWithoutExtension}-$timestamp.yml")
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            SkRedisLogger.success(Main.INSTANCE, "Backup created successfully at ${backupFile.absolutePath}")
        } catch (e: Exception) {
            SkRedisLogger.error(Main.INSTANCE, "Failed to create backup of config file: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeRecursive(defaultMap: Map<String, Any>, loadedMap: MutableMap<String, Any>, path: String): Boolean {
        var changed = false
        // Remove unknown keys
        val keysToRemove = loadedMap.keys.filter { !defaultMap.containsKey(it) }.toList()
        if (keysToRemove.isNotEmpty()) {
            SkRedisLogger.warn(Main.INSTANCE, "Found unknown keys at $path: $keysToRemove")
        }
        for (key in keysToRemove) {
            loadedMap.remove(key)
            changed = true
            SkRedisLogger.info(Main.INSTANCE, "Removed unknown key: $path.$key")
        }

        // Add missing keys or merge nested maps
        defaultMap.forEach { (key, defaultValue) ->
            if (!loadedMap.containsKey(key) || loadedMap[key] == null) {
                loadedMap[key] = defaultValue
                changed = true
                SkRedisLogger.warn(Main.INSTANCE, "Added missing key: $path.$key")
            } else if (defaultValue is Map<*, *> && loadedMap[key] is Map<*, *>) {
                val nestedDefault = defaultValue as Map<String, Any>
                val nestedLoaded = (loadedMap[key] as Map<String, Any>).toMutableMap()
                if (mergeRecursive(nestedDefault, nestedLoaded, "$path.$key")) {
                    loadedMap[key] = nestedLoaded
                    changed = true
                }
            }
        }
        return changed
    }
}