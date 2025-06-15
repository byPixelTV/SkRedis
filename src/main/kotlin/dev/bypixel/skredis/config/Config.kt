package dev.bypixel.skredis.config

data class Config(
    var configVersion: Int = 1,
    var updateChecker: Boolean = true,
    var redis: RedisConfig = RedisConfig(),
    var redivelocity: RediVelocityConfig = RediVelocityConfig(),
    var channels: List<String> = listOf("global", "servername", "messaging", "utility1")
) {
    data class RedisConfig(
        var host: String = "127.0.0.1",
        var port: Int = 6379,
        var username: String = "default",
        var password: String = "password",
        var useSsl: Boolean = false,
        var maxConnections: Int = 10,
        var timeout: Int = 9000,
        var useCustomMessageFormat: Boolean = false,
        var messageFormat: String = "{\"message\": \"%message%\", \"timestamp\": \"%timestamp%\"}"
    )

    data class RediVelocityConfig(
        var enabled: Boolean = false
    )
}