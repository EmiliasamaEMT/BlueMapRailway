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
        // Gradle does not infer values used by expand() as task inputs.
        // Track the version explicitly so a new -PpluginVersion cannot reuse
        // an old plugin.yml from an up-to-date resource task.
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
