plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.experimento"
// Mantener alineado con com.experimento.launcher.LauncherMetadata.VERSION
version = "1.1.0-pre.1"

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

/**
 * Los scripts de distribución de Gradle solo ponen dependencias en `-classpath`.
 * JavaFX 11+ es modular: hay que poner los JAR de OpenJFX en `--module-path` y
 * declarar `--add-modules`; si no, en Linux/macOS/Windows aparece el error
 * «faltan los componentes de JavaFX runtime…» al ejecutar `bin/experimento-launcher`.
 */
val runtimeClasspath = configurations.named("runtimeClasspath").get()
val javafxRuntimeJars =
    runtimeClasspath
        .filter { it.name.startsWith("javafx-") }
        .files
        .sortedBy { it.name }

tasks.named<org.gradle.jvm.application.tasks.CreateStartScripts>("startScripts") {
    // Al fijar `classpath` se sustituye el valor por defecto; hay que seguir incluyendo el JAR del proyecto.
    classpath =
        tasks.named<Jar>("jar").get().outputs.files +
            runtimeClasspath.filter { !it.name.startsWith("javafx-") }

    doLast {
        val unixScript = layout.buildDirectory.file("scripts/experimento-launcher").get().asFile
        val windowsScript = layout.buildDirectory.file("scripts/experimento-launcher.bat").get().asFile

        val unixModulePath = javafxRuntimeJars.joinToString(":") { "\$APP_HOME/lib/${it.name}" }
        val jfxOpts =
            "--module-path $unixModulePath --add-modules javafx.controls,javafx.fxml,javafx.web"
        unixScript.writeText(
            unixScript.readText().replace("DEFAULT_JVM_OPTS=\"\"", "DEFAULT_JVM_OPTS=\"$jfxOpts\""),
        )

        val winModulePath = javafxRuntimeJars.joinToString(";") { "%APP_HOME%\\lib\\${it.name}" }
        val winJfxOpts =
            "--module-path $winModulePath --add-modules javafx.controls,javafx.fxml,javafx.web"
        windowsScript.writeText(
            windowsScript
                .readText()
                .replace("set DEFAULT_JVM_OPTS=", "set DEFAULT_JVM_OPTS=$winJfxOpts "),
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}
