plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    // Android supplies org.json at runtime; don't package a duplicate in the APK.
    compileOnly("org.json:json:20250517")
    testImplementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}
