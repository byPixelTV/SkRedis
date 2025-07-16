import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    kotlin("jvm") version "2.2.0"
    id("de.eldoria.plugin-yml.paper") version "0.7.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.0-SNAPSHOT"
}

val versionString = "1.2.3-SNAPSHOT"

group = "dev.bypixel"
version = versionString

repositories {
    mavenCentral()

    maven("https://jitpack.io")

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.skriptlang.org/releases")
    }
}

val commandAPIVersion = "10.1.1"
val skriptVersion = "2.12.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")

    library("dev.jorel:commandapi-bukkit-shade-mojang-mapped:$commandAPIVersion")
    library("dev.jorel:commandapi-bukkit-kotlin:$commandAPIVersion")
    library("org.yaml:snakeyaml:2.4")
    library("net.axay:kspigot:1.21.0")
    library("redis.clients:jedis:6.0.0")
    library(kotlin("stdlib"))

    compileOnly("com.github.SkriptLang:Skript:$skriptVersion")

    library("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
}

sourceSets {
    getByName("main") {
        java {
            srcDir("src/main/kotlin")
        }
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    shadowJar {
        relocate("com.tcoded.folialib", "dev.bypixel.skredis.lib.folialib")

        archiveBaseName.set("SkRedis")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")
    }

    runServer {
        minecraftVersion("1.21.5")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/$skriptVersion/Skript-${skriptVersion}.jar")
            url("https://cdn.modrinth.com/data/P1OZGk5p/versions/c7qUCKzX/ViaVersion-5.4.0-SNAPSHOT.jar")
            url("https://cdn.modrinth.com/data/NpvuJQoq/versions/dtrTeZLl/ViaBackwards-5.4.0-SNAPSHOT.jar")
        }
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

tasks.jar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

// if you have shadowJar configured
tasks.shadowJar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

paper {
    main = "dev.bypixel.skredis.Main"

    loader = "dev.bypixel.skredis.SkRedisPluginLoader"
    hasOpenClassloader = false

    generateLibrariesJson = true

    authors = listOf("byPixelTV")

    apiVersion = "1.21.7"

    version = versionString

    foliaSupported = false

    prefix = "SkRedis"

    serverDependencies {
        // During server run time, require Skript, add it to the classpath, and load it before us
        register("Skript") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}