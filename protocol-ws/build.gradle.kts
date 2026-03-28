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
    implementation("io.netty:netty-all:4.1.108.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation(project(":netty-core"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}
