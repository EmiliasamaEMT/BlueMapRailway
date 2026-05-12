plugins {
    java
}

group = "io.github.emiliasamaemt"
version = providers.gradleProperty("pluginVersion").orElse("0.1.4-SNAPSHOT").get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("de.bluecolored:bluemap-api:${property("blueMapApiVersion")}")
}

tasks {
    jar {
        archiveBaseName.set("BlueMapRailway")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
