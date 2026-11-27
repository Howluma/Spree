plugins {
    java
}

group = "dev.howluma"
version = "1.7.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Bukkit classes must also be present when the JVM loads SkriptImporter in tests.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("SpreeCore-${project.version}.jar")
}

// Isolated verification branch: bundle the resolved real Paper API and dependencies
// for compiling the 1.8.68 patch against the existing 1.8.67 binary.
val paperLibraries by tasks.registering(Zip::class) {
    archiveFileName.set("paper-libraries.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(configurations.testRuntimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.build {
    dependsOn(paperLibraries)
}
