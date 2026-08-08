plugins {
    id("java-library")
    alias(libs.plugins.conventions.java)
    alias(libs.plugins.conventions.publishing)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
}

earthmc {
    publishing {
        public = true
    }
}
