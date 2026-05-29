pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.bluecolored.de/releases/")
    }
}

rootProject.name = "BlueMapRailway"
include("core", "paper", "fabric")
