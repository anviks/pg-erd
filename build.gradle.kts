plugins {
    java
    application
}

group = "io.dbeavererd"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.postgresql:postgresql:42.7.14")
    // Eclipse Layout Kernel: layered layout with ports and orthogonal edge routing
    implementation("org.eclipse.elk:org.eclipse.elk.core:0.12.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.12.0")
    // ELK's generated option classes use Xtext's xbase runtime library (declared "provided" upstream)
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.44.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"("org.testcontainers:postgresql:1.21.4")
}

application {
    mainClass = "io.dbeavererd.cli.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    systemProperty("regolden", project.findProperty("regolden") ?: "false")
    systemProperty("java.awt.headless", "true")
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs Testcontainers-backed tests against a real PostgreSQL (needs Docker)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    shouldRunAfter(tasks.test)
}

// Single runnable jar with all runtime dependencies merged in (only picocli + pgjdbc).
tasks.jar {
    archiveFileName = "dbeaver-erd.jar"
    manifest {
        attributes("Main-Class" to "io.dbeavererd.cli.Main")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/9/module-info.class", "module-info.class")
    }
}
