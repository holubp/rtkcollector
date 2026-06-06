plugins {
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "org.rtkcollector"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.register("assembleDebug") {
    description = "Bootstrap alias for Android-style debug assembly until the Android app module is enabled."
    group = "build"
    dependsOn(
        provider {
            subprojects
                .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.jvm") }
                .map { it.tasks.named("assemble") }
        },
    )
}
