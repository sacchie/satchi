plugins {
    kotlin("jvm") version "1.5.10"
    application
    id("com.diffplug.spotless") version "5.17.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:3.13.10")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.5")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.5")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.5")
    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation("com.rometools:rome:1.16.0")
    implementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("io.strikt:strikt-core:0.32.0")
}

spotless {
    kotlin {
        ktlint().userData(mapOf("disabled_rules" to "no-wildcard-imports"))
    }
}

application {
    mainClass.set("main.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
