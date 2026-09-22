plugins {
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("dsc.web.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("io.javalin:javalin:7.2.3")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<JavaExec>("run") {
    // Pass CLI args through (--host/--port are parsed by dsc.web.Main)
}
