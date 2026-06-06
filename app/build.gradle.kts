plugins {
    id("com.android.application")
}

android {
    namespace = "org.rtkcollector.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rtkcollector.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:workflow"))
}
