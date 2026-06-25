plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":receiver:api"))
    implementation(project(":core:quality"))
    implementation(project(":core:solution"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
