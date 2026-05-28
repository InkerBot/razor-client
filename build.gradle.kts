plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    id("bc-i18n")
    kotlin("jvm") version "2.3.10" apply false
}

val releaseVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("RELEASE_VERSION"))
    .orElse("1.0-SNAPSHOT")

allprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    if (!project.path.startsWith(":transport")) {
        apply(plugin = "kotlin")
    }

    group = "bot.inker.bc"
    version = releaseVersion.get()

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

rootProject.allprojects.filter { !it.path.startsWith(":application") }.forEach { publishedProject ->
    publishedProject.apply(plugin = "maven-publish")
    val javaComponent = publishedProject.components["java"]

    publishedProject.extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("mavenJava") {
                from(javaComponent)
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "InkerBot/razor-client"}")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

dependencies {
    api(project(":transport"))
    api("com.google.code.gson:gson:2.13.1")

    testRuntimeOnly(project(":transport:socketio"))
}
