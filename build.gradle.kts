import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    kotlin("jvm") version "2.2.20"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.2.2"
}

fun getLatestTag(): String {
    try {
        // fetch all tags (remote + local)
        ProcessBuilder("git", "fetch", "--tags")
            .redirectErrorStream(true)
            .start()
            .apply {
                inputStream.bufferedReader().use { it.readText() }
                waitFor()
            }

        // get current branch
        val branch = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .use { it.readText().trim() }

        // get latest tag
        val tagProcess = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()

        val rawTag = tagProcess.inputStream.bufferedReader().use { it.readText().trim() }
        tagProcess.waitFor()

        if (rawTag.isEmpty()) return "unknown"

        val tag = rawTag.removePrefix("v")

        return if (branch == "release") {
            tag
        } else {
            // get short commit hash
            val commitProcess = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectErrorStream(true)
                .start()
            val commit = commitProcess.inputStream.bufferedReader().use { it.readText().trim() }
            commitProcess.waitFor()

            "$tag+$commit"
        }
    } catch (e: Exception) {
        return "unknown"
    }
}

val versionString = getLatestTag()

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

val commandAPIVersion = "10.1.2"
val skriptVersion = "2.13.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    library("dev.jorel:commandapi-bukkit-shade-mojang-mapped:$commandAPIVersion")
    library("dev.jorel:commandapi-bukkit-kotlin:$commandAPIVersion")
    library("org.yaml:snakeyaml:2.5")
    library("net.axay:kspigot:1.21.0")
    library("io.lettuce:lettuce-core:6.8.1.RELEASE") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-reactive")
    }
    library("org.json:json:20250517")

    library(kotlin("stdlib"))

    compileOnly("com.github.SkriptLang:Skript:$skriptVersion")

    library("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")

    implementation("com.github.Anon8281:UniversalScheduler:0.1.7")
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
        archiveBaseName.set("SkRedis")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }

        minimize()

        relocate("com.github.Anon8281.universalScheduler", "dev.bypixel.skredis.lib.universalscheduler")
    }

    runServer {
        minecraftVersion("1.21.10")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/$skriptVersion/Skript-${skriptVersion}.jar")
            url("https://github.com/SkriptHub/SkriptHubDocsTool/releases/download/1.14/skripthubdocstool-1.14.jar")
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

tasks {
    generatePaperPluginDescription {
        useDefaultCentralProxy()
    }
}

paper {
    main = "dev.bypixel.skredis.Main"

    loader = "dev.bypixel.skredis.SkRedisPluginLoader"
    hasOpenClassloader = false

    generateLibrariesJson = true

    authors = listOf("byPixelTV")

    apiVersion = "1.21"

    version = versionString

    foliaSupported = true

    description = "A Skript-Addon to interact with Redis."

    prefix = "SkRedis"

    serverDependencies {
        // During server run time, require Skript, add it to the classpath, and load it before us. paper plugins require the joinClasspath to be true to work.
        register("Skript") {
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}

kotlin {
    jvmToolchain(21)
}