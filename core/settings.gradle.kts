// Allows core tests to run without an Android SDK: ../gradlew -p . test
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins { id("org.jetbrains.kotlin.jvm") version "2.1.20" }
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "returnright-core"
