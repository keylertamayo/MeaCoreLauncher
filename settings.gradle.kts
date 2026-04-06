plugins {
    // Permite que Gradle descargue un JDK 21 completo (con javac) si el del sistema no sirve para toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "experimento-launcher"
