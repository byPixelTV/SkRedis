import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    kotlin("jvm") version "2.2.10"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.0"
    id("com.gradleup.shadow") version "9.1.0"
}

val versionString = "2.0.1"

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
val skriptVersion = "2.12.2"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

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

    library("org.jetbrains.kotlin:kotlin-reflect:2.2.10")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")
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
    }

    runServer {
        minecraftVersion("1.21.7")

        downloadPlugins {
            url("https://github.com/SkriptLang/Skript/releases/download/$skriptVersion/Skript-${skriptVersion}.jar")
            url("https://github.com/SkriptHub/SkriptHubDocsTool/releases/download/1.14/skripthubdocstool-1.14.jar")
            modrinth("viaversion", "5.4.2")
            modrinth("viabackwards", "5.4.2")
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

    apiVersion = "1.21.8"

    version = versionString

    foliaSupported = false

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