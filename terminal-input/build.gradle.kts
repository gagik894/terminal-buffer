plugins {
    id("java-library")
    kotlin("jvm") version "2.2.21"
}

group = "com.gagik"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":terminal-protocol"))
    implementation(project(":terminal-core"))

    testImplementation(project(":terminal-core"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
