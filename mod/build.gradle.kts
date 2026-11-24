// Krovex Client - the installable Fabric mod half of the project.
//
// IMPORTANT (see top-level README "Known limitations"): this module cannot be
// compiled in the sandbox this was built in -- Fabric Loom needs to download
// remapped Minecraft + Yarn mappings from Mojang's and FabricMC's Maven repositories,
// both of which are outside this environment's network allowlist. The build file
// below is a correct, standard Fabric Loom setup; running `./gradlew :mod:build`
// on a machine with normal internet access is expected to work as written.
plugins {
    id("fabric-loom") version "1.19.4"
    `java-library`
}

val minecraftVersion = "1.21.11"
val yarnMappings = "1.21.11+build.6"
val loaderVersion = "0.20.1"
val fabricApiVersion = "0.141.6+1.21.11"

// Loom's remapJar (the jar that actually ships) takes its name from base.archivesName,
// not from the jar task's archiveBaseName below -- without this it comes out as mod-<v>.jar.
base {
    archivesName.set("Krovex-Client-$minecraftVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":shared-theme"))
}

loom {
    // The shared-theme classes need to end up inside the mod jar (Krovex-Client-*.jar
    // must work standalone in any Fabric-compatible launcher), not as a separate
    // mod dependency the user would also have to install.
}

val sharedThemeClasses = project(":shared-theme").layout.buildDirectory.dir("classes/java/main")

tasks.named<Jar>("jar") {
    dependsOn(":shared-theme:classes")
    from(sharedThemeClasses)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("Krovex-Client-$minecraftVersion")
    archiveVersion.set(project.version.toString())
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion
        ))
    }
}



tasks.register("printCompileInfo") {    doLast {
        println("TOOLCHAIN: " + project.extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.get())
        project.configurations.getByName("compileClasspath").files.forEach { f ->
            println("CP: " + f.name + " size=" + f.length())
        }
    }
}
