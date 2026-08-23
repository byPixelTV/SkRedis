import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    kotlin("jvm") version "2.4.10"
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
    id("org.bxteam.quark") version "1.3.0"
}

fun getLatestTag(): String {
    try {
        // Fetch all tags
        ProcessBuilder("git", "fetch", "--tags")
            .redirectErrorStream(true)
            .start()
            .apply {
                inputStream.bufferedReader().use { it.readText() }
                waitFor()
            }

        val branch = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .use { it.readText().trim() }

        // Try to get latest tag
        val tagProcess = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()
        val rawTag = tagProcess.inputStream.bufferedReader().use { it.readText().trim() }
        tagProcess.waitFor()

        val hasTag = rawTag.isNotEmpty() && !rawTag.startsWith("fatal:")

        // Always get commit hash (works even if no tag)
        val commitProcess = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
        val commit = commitProcess.inputStream.bufferedReader().use { it.readText().trim() }
        commitProcess.waitFor()

        // If no commit found (super rare, empty repo)
        if (commit.isEmpty()) return "unknown"

        return if (hasTag) {
            val tag = rawTag.removePrefix("v")
            if (branch == "release") tag else "$tag+$commit"
        } else {
            // no tag → default to 1.0.0 + commit
            "1.0.0+$commit"
        }
    } catch (_: Exception) {
        return "unknown"
    }
}

val versionString = getLatestTag()

group = "dev.bypixel"
version = versionString

repositories {
    mavenCentral()

    maven("https://jitpack.io")
    maven("https://repo.bxteam.org/releases")
    maven("https://repo.pauli.fyi/releases")

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.skriptlang.org/releases")
    }
}

val skriptVersion = "2.16.1"

dependencies {
    library(kotlin("stdlib"))

    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    library("dev.dejvokep:boosted-yaml:1.3.7")
    library("net.axay:kspigot:1.22.0")
    library("io.github.classgraph:classgraph:4.8.193")
    quark("com.github.bypixeltv:LettuceWrapper:nightly-SNAPSHOT") {
        exclude(group = "io.netty", module = "netty-common")
    }
    quark("io.netty:netty-common:4.2.17.Final")

    compileOnly("com.github.SkriptLang:Skript:$skriptVersion")

    implementation("com.github.Anon8281:UniversalScheduler:0.1.7")
}

quark {
    platform = "paper"

    repositories {
        includeProjectRepositories()
    }

    relocate("io.lettuce", "dev.bypixel.skredis.lib.lettuce")
    relocate("io.netty", "dev.bypixel.skredis.lib.netty")
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
        options.release.set(25)
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
        minecraftVersion("26.2")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/$skriptVersion/Skript-${skriptVersion}.jar")
            url("https://github.com/SkriptHub/SkriptHubDocsTool/releases/download/1.17/skripthubdocstool-1.14.jar")
        }
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
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
    main = "dev.bypixel.skredis.SkRedis"

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
    jvmToolchain(25)
}
