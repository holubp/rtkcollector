plugins {
    id("com.android.application") version "9.2.0" apply false
    kotlin("android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    kotlin("jvm") version "2.3.21" apply false
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

if (tasks.findByName("assembleDebug") == null) {
    tasks.register("assembleDebug") {
        description = "Runs debug assembly for the Android app when present."
        group = "build"
        dependsOn(":app:assembleDebug")
    }
}
