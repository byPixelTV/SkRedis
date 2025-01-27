import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    kotlin("jvm") version "2.1.10"
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
    id("com.gradleup.shadow") version "9.0.0-SNAPSHOT"
    id("io.papermc.paperweight.userdev") version "2.0.0-SNAPSHOT"
}


val versionString = "1.1.0"

group = "de.bypixeltv"
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

val commandAPIVersion = "9.7.0"

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")

    library("dev.jorel:commandapi-bukkit-shade-mojang-mapped:$commandAPIVersion")
    library("dev.jorel:commandapi-bukkit-kotlin:$commandAPIVersion")
    library("net.kyori:adventure-platform-bukkit:4.3.4")
    library("net.kyori:adventure-text-minimessage:4.18.0")

    compileOnly("com.github.SkriptLang:Skript:2.10.0")
    implementation("com.github.technicallycoded:FoliaLib:main-SNAPSHOT")

    library("net.axay:kspigot:1.21.0")
    library("redis.clients:jedis:5.2.0")

    library(kotlin("stdlib"))
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

    named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java)

    shadowJar {
        relocate("com.tcoded.folialib", "de.bypixeltv.skredis.lib.folialib")

        archiveBaseName.set("SkRedis")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

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
    main = "de.bypixeltv.skredis.Main"

    loader = "de.bypixeltv.skredis.SkRedisPluginLoader"
    hasOpenClassloader = false

    generateLibrariesJson = true

    authors = listOf("byPixelTV")

    apiVersion = "1.21.4"

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