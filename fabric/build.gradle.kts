dependencies {
    implementation(project(":core"))
}

tasks {
    jar {
        archiveBaseName.set("BlueMapRailway-fabric")
    }
}
