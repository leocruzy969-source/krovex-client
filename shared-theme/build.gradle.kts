// Shared theme format used by both the launcher (Swing) and the mod (Minecraft GUI).
// Deliberately has ZERO external dependencies: no Gson, no Jackson. The mod module
// runs inside a game that already ships Gson under a relocated package that varies
// by Minecraft/Fabric version, and the launcher is a standalone JDK app with nothing
// on its classpath — a dependency-free, hand-rolled JSON reader/writer here guarantees
// both sides parse/produce byte-identical `.krovextheme` files without version skew.
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
