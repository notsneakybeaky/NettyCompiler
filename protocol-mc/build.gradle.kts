plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Netty for ByteBuf in MC codec
    implementation("io.netty:netty-all:4.1.108.Final")

    // Core interfaces
    implementation(project(":netty-core"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}
