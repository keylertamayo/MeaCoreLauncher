import java.util.Properties

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.experimento"

val versionProps = Properties()
file("src/main/resources/version.properties").inputStream().use { versionProps.load(it) }
version = versionProps.getProperty("version")

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
    
    // Diagnóstico de Hardware
    implementation("com.github.oshi:oshi-core:6.6.3")
    implementation("net.java.dev.jna:jna:5.14.0")

    val junit = "5.10.3"
    testImplementation("org.junit.jupiter:junit-jupiter:$junit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.experimento.launcher.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

val copyDependencies by tasks.registering(Copy::class) {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libs"))
}

tasks.named<Jar>("jar") {
    dependsOn(copyDependencies)
    manifest {
        attributes["Main-Class"] = "com.experimento.launcher.Main"
        // Crea el Class-Path del manifiesto uniendo los nombres de todas las dependencias
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(" ") { it.name }
    }
}

