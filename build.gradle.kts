plugins {
    base
}

group = "io.github.emiliasamaemt"
version = providers.gradleProperty("pluginVersion").orElse("0.1.16-SNAPSHOT").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}

val syncPaperReleaseJar by tasks.registering(Sync::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Copy the Paper release jar into the root build/libs directory."

    val paperJar = project(":paper").tasks.named<Jar>("jar")
    dependsOn(paperJar)

    from(paperJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("assemble") {
    dependsOn(syncPaperReleaseJar)
}
