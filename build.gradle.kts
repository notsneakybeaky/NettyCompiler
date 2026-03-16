plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.nettyruntime"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Netty
    implementation("io.netty:netty-all:4.1.108.Final")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Protocol MC subproject
    implementation(project(":protocol-mc"))
}

application {
    mainClass = "com.nettyruntime.Main"
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName = "netty-runtime"
    archiveClassifier = ""
    archiveVersion = ""
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
}
