plugins {
    kotlin("jvm") version "2.2.21"
}

group = "com.gagik"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":terminal-core"))
    implementation(project(":terminal-parser"))
    implementation(project(":terminal-integration"))
    implementation(project(":terminal-input"))
    implementation(project(":terminal-protocol"))
    implementation(project(":terminal-transport-api"))

    testImplementation(project(":terminal-testkit"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
