package dev.bypixel.skredis.utils.update

import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.SkRedisLogger
import dev.dejvokep.boostedyaml.route.Route
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object UpdateUtil {
    private var latestVersionCache: String? = null

    val updateJob = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            SkRedisLogger.info("Checking for updates...")
            val latestVersionString = getLatestVersion()
            val currentVersionString = SkRedis.instance.pluginMeta.version
            val latestVersion = Version.fromString(latestVersionString)
            val currentVersion = Version.fromString(currentVersionString)
            val compare = latestVersion.compareTo(currentVersion)

            latestVersionCache = latestVersionString

            if (currentVersionString.contains("+")) {
                SkRedisLogger.consoleMessage("<yellow>Skipping update check for <color:#ff0000><b>development build,</b></color> things may not work as expected, please report any bugs on <aqua>GitHub</aqua></yellow>")
                SkRedisLogger.consoleMessage("<aqua><b>https://github.com/byPixelTV/SkRedis/issues</b></aqua>")
                delay(30 * 60 * 1000L)
                continue
            }

            if (compare == 0) {
                SkRedisLogger.success("<green>The plugin is up to date! (v$currentVersionString)</green>")
            } else if (compare < 0) {
                SkRedisLogger.success("<yellow>You are running a newer version ($currentVersionString) than the latest release (v$latestVersionString).</yellow>")
            } else {
                SkRedisLogger.consoleMessage("<red>The plugin is not up to date!</red>")
                SkRedisLogger.consoleMessage(" - Current version: <red>v$currentVersionString</red>")
                SkRedisLogger.consoleMessage(" - Available update: <green>v$latestVersionString</green>")
                SkRedisLogger.consoleMessage(" - Download available at: <aqua>https://github.com/byPixelTV/SkRedis/releases</aqua>")
            }
            delay(SkRedis.instance.config.getInt(Route.fromString("update-check.check-interval")) * 1000L) // Check every n seconds
        }
    }

    fun getLatestCachedVersion(): String? {
        return latestVersionCache
    }

    suspend fun getLatestVersion(): String = withContext(Dispatchers.IO) {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/byPixelTV/SkRedis/releases/latest"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw Exception("Unexpected code ${response.statusCode()}")
        }

        val body = response.body()
        val jBody = JSONObject(body)
        jBody.getString("tag_name").removePrefix("v")
    }
}