plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.experimento"
// Mantener alineado con com.experimento.launcher.LauncherMetadata.VERSION
version = "0.1.0-pre.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("net.kyori:adventure-nbt:4.17.0")

    val junit = "5.10.3"
    testImplementation("org.junit.jupiter:junit-jupiter:$junit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

application {
    mainClass.set("com.experimento.launcher.LauncherApp")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
