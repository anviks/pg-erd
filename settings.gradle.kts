plugins {
    // Lets Gradle download a JDK for the configured toolchain when none is installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dbeaver-erd"
