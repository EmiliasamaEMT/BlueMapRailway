import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.14.7"
}

val shade by configurations.creating

repositories {
    mavenCentral()
    maven("https://repo.bluecolored.de/releases/")
}

dependencies {
    implementation(project(":core"))
    minecraft("com.mojang:minecraft:${property("fabricMinecraftVersion")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}")
    compileOnly("de.bluecolored:bluemap-api:${property("blueMapApiVersion")}")
    implementation("org.yaml:snakeyaml:2.4")
    shade("org.yaml:snakeyaml:2.4")
}

loom {
    serverOnlyMinecraftJar()
}

tasks {
    jar {
        archiveBaseName.set("BlueMapRailway-fabric")
        from(project(":core").extensions.getByType<SourceSetContainer>().named("main").map { it.output })
        from({
            shade.files.filter { it.exists() }.map { zipTree(it) }
        })
    }

    named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        archiveBaseName.set("BlueMapRailway-fabric")
    }

    processResources {
        filteringCharset = "UTF-8"
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(21)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
