// Root build file. Individual modules configure themselves; this just pins
// a consistent Java toolchain version across the whole project.
allprojects {
    group = "com.krovex"
    version = "0.1.54"

    repositories {
        mavenCentral()
    }

    // Gradle itself runs on JDK 25 (required by Loom 1.19.4), but the mod must be
    // loadable by Minecraft's Java 21 runtime, so emit Java 21 bytecode (class
    // file version 65) instead of the JDK's default version 69.
    tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.release.set(21)
    }
}
