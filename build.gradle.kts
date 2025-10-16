plugins {
    java
}

group = "com.markmode"
version = "1.0.0"
description = "Economy + NPC Shop (GUI) for Paper 1.21.x"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("version" to version))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
