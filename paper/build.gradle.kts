import org.gradle.api.tasks.SourceSetContainer

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("de.bluecolored:bluemap-api:${property("blueMapApiVersion")}")
}

tasks {
    jar {
        archiveBaseName.set("BlueMapRailway")
        from(project(":core").extensions.getByType<SourceSetContainer>().named("main").map { it.output })
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
