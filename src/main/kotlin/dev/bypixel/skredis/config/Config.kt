package dev.bypixel.skredis.config

data class Config(
    var configVersion: Int = 2,
    var updateChecker: Boolean = true,
    var redis: RedisConfig = RedisConfig(),
) {
    data class RedisConfig(
        var host: String = "127.0.0.1",
        var port: Int = 6379,
        var username: String = "default",
        var password: String = "password",
        var useSsl: Boolean = false,
        var maxConnections: Int = 10,
        var timeout: Int = 9000,
    )
}