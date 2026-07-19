import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun localSdkDir(): File? {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.isFile) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { return file(it) }
    }
    return sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .map(::file)
        .firstOrNull { it.isDirectory }
}

val rtklibNativeBuildAvailable = localSdkDir()
    ?.resolve("ndk")
    ?.listFiles()
    ?.any { it.resolve("source.properties").isFile }
    ?: false
val rtklibNativeReleaseTaskNames = setOf(
    "assembleRelease",
    "bundleRelease",
    "validateGooglePlayReleaseBuildInputs",
    "validateGooglePlayReleaseBundle",
)
val appCompileSdkVersion = 36
val rtklibNativeReleaseRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(":") in rtklibNativeReleaseTaskNames
}

if (rtklibNativeReleaseRequested && !rtklibNativeBuildAvailable) {
    throw GradleException(
        "Release builds require Android NDK so librtkcollector_rtklib.so is packaged. " +
            "Install an Android NDK in Android Studio/SDK Manager before building a Google Play release.",
    )
}

android {
    namespace = "org.rtkcollector.app"
    compileSdk = appCompileSdkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "org.rtkcollector.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0-RC1"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    if (rtklibNativeBuildAvailable) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(project(":core:capture"))
    implementation(project(":core:correction"))
    implementation(project(":core:quality"))
    implementation(project(":core:session"))
    implementation(project(":core:transport"))
    implementation(project(":core:workflow"))
    implementation(project(":core:rtklib"))
    implementation(project(":receiver:api"))
    implementation(project(":receiver:unicore-n4"))
    implementation(project(":receiver:ublox-m8"))
    implementation(project(":core:solution"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.14.1")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register("unitTestClasses") {
    group = "build"
    description = "Compatibility alias for tools that request JVM-style unit test classes in the Android app module."
    dependsOn("compileDebugUnitTestKotlin", "compileDebugUnitTestJavaWithJavac", "processDebugUnitTestJavaRes")
}

tasks.register("androidTestClasses") {
    group = "build"
    description = "Compatibility alias for tools that request Android instrumentation test classes in the app module."
    dependsOn("compileDebugAndroidTestSources")
}

val termuxDebugUnitTestClasses = files(
    layout.buildDirectory.dir("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"),
    layout.buildDirectory.dir("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
)
val termuxDebugMainClasses = files(
    layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
    layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    layout.buildDirectory.file("intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar"),
    layout.buildDirectory.dir("intermediates/java_res/debugUnitTest/processDebugUnitTestJavaRes/out"),
)
val termuxAndroidJar = localSdkDir()?.resolve("platforms/android-$appCompileSdkVersion/android.jar")

tasks.register<org.gradle.api.tasks.testing.Test>("termuxTestDebugUnitTest") {
    group = "verification"
    description = "Runs debug JVM tests from compiled classes without Android resource linking."
    dependsOn("unitTestClasses")
    doFirst {
        require(termuxAndroidJar?.isFile == true) {
            "Termux unit tests require the Android $appCompileSdkVersion platform android.jar."
        }
    }
    testClassesDirs = termuxDebugUnitTestClasses
    classpath = termuxDebugUnitTestClasses + termuxDebugMainClasses + files(provider {
        configurations.getByName("debugUnitTestRuntimeClasspath")
            .incoming
            .artifactView {
                attributes.attribute(
                    org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                    "android-classes-jar",
                )
                componentFilter { identifier ->
                    identifier !is org.gradle.api.artifacts.component.ProjectComponentIdentifier ||
                        identifier.projectPath != project.path
                }
            }
            .files
            .files
    }) + files(termuxAndroidJar)
    useJUnitPlatform()
    filter {
        excludeTestsMatching("org.rtkcollector.app.mocklocation.MockLocationPublisherTest")
        excludeTestsMatching("org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest")
        excludeTestsMatching("org.rtkcollector.app.ui.imports.SettingsImportIntentReaderTest")
    }
}

fun validateReleaseBundleNativeLibrary(bundleFile: File) {
    if (!bundleFile.isFile) {
        throw GradleException("Release bundle was not created at ${bundleFile.path}.")
    }
    ZipFile(bundleFile).use { zip ->
        val rtklibEntries = listOf(
            "base/lib/armeabi-v7a/librtkcollector_rtklib.so",
            "base/lib/arm64-v8a/librtkcollector_rtklib.so",
        )
        val missingEntries = rtklibEntries.filter { zip.getEntry(it) == null }
        if (missingEntries.isNotEmpty()) {
            throw GradleException(
                "Release bundle is missing RTKLIB native libraries: ${missingEntries.joinToString()}.",
            )
        }
    }
}

val validateGooglePlayReleaseBuildInputs = tasks.register("validateGooglePlayReleaseBuildInputs") {
    group = "verification"
    description = "Checks that Google Play release builds cannot silently omit the RTKLIB native backend."
    doLast {
        if (!rtklibNativeBuildAvailable) {
            throw GradleException(
                "Android NDK was not found. Release bundles must include librtkcollector_rtklib.so.",
            )
        }
        val rtklibSource = rootProject.file("third_party/rtklib-ex/upstream/src/rtklib.h")
        if (!rtklibSource.isFile) {
            throw GradleException(
                "RTKLIB-EX source checkout is missing at ${rtklibSource.path}. Run tools/update_rtklib_ex.py first.",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(validateGooglePlayReleaseBuildInputs)
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(validateGooglePlayReleaseBuildInputs)
    doLast {
        val bundleFile = layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile
        validateReleaseBundleNativeLibrary(bundleFile)
    }
}

tasks.register("validateGooglePlayReleaseBundle") {
    group = "verification"
    description = "Builds the release AAB and verifies it contains the RTKLIB native library for Play delivery."
    dependsOn("bundleRelease")
}
