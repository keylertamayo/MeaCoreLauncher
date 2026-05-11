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
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    
    // Bytecode Manipulation - ASM para Java Agent
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-util:9.7")

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

tasks.withType<JavaExec> {
    // Propiedades GTK necesarias solo en Linux para que la ventana tenga el WM_CLASS correcto
    val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")
    if (isLinux) {
        jvmArgs(
            "-Dcom.sun.javafx.wm.class=meacorelauncher",
            "-Dglass.gtk.wm_class=meacorelauncher",
            "-Djdk.gtk.wm_class=meacorelauncher"
        )
    }
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

val copyJavaFXDependencies by tasks.registering(Copy::class) {
    val javaFXModules = configurations.runtimeClasspath.get().files.filter { 
        it.name.startsWith("javafx-") 
    }
    from(javaFXModules)
    into(layout.buildDirectory.dir("libs"))
}

tasks.named<Jar>("jar") {
    dependsOn(copyDependencies)
    dependsOn(copyJavaFXDependencies)
    manifest {
        attributes["Main-Class"] = "com.experimento.launcher.Main"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(" ") { it.name }
    }
}

tasks.register<Jar>("agentJar") {
    archiveBaseName.set("meacore-agent")
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Premain-Class" to "com.experimento.launcher.agent.LanguageFilterAgent",
            "Agent-Class" to "com.experimento.launcher.agent.LanguageFilterAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
    
    from(sourceSets.main.output().classesDirs) {
        include("com/experimento/launcher/agent/**")
    }
}

tasks.register<Copy>("copyAgentToLibs") {
    from(tasks.named<Jar>("agentJar")) {
        rename { "meacore-agent.jar" }
    }
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("jar") {
    dependsOn("copyAgentToLibs")
}
