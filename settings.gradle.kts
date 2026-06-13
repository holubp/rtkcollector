pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "rtkcollector"

include(":app")
include(":core:transport")
include(":core:capture")
include(":core:correction")
include(":core:session")
include(":core:workflow")
include(":core:quality")
include(":core:solution")
include(":receiver:api")
include(":receiver:generic-nmea-rtcm")
include(":receiver:unicore-n4")
include(":receiver:ublox-m8")
