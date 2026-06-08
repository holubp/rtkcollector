plugins {
    id("com.android.application")
}

val localCompileSdk = providers.gradleProperty("termuxCompileSdk")
    .map(String::toInt)
    .getOrElse(36)

val localTargetSdk = providers.gradleProperty("termuxTargetSdk")
    .map(String::toInt)
    .getOrElse(36)

android {
    namespace = "org.rtkcollector.app"
    compileSdk = localCompileSdk

    defaultConfig {
        applicationId = "org.rtkcollector.app"
        minSdk = 26
        targetSdk = localTargetSdk
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:capture"))
    implementation(project(":core:correction"))
    implementation(project(":core:session"))
    implementation(project(":core:transport"))
    implementation(project(":core:workflow"))
    implementation(project(":receiver:unicore-n4"))
}
