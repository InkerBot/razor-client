plugins {
    id("java")
    id("java-library")
    id("bc-i18n")
    kotlin("jvm") version "2.3.10" apply false
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    if (!project.path.startsWith(":transport")) {
        apply(plugin = "kotlin")
    }

    group = "bot.inker.bc"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

dependencies {
    api(project(":transport"))
    api("com.google.code.gson:gson:2.13.1")

    testRuntimeOnly(project(":transport:socketio"))
}
