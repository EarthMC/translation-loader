pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.earthmc.net/public")
    }
}

rootProject.name = "translation-loader"

include("loader", "test-plugin")
