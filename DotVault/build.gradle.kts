plugin {
    id("application")
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "com.dotvault"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // JavaFX
    implementation("org.openjfx:javafx-base:21.0.1")
    implementation("org.openjfx:javafx-graphics:21.0.1")
    implementation("org.openjfx:javafx-controls:21.0.1")
    implementation("org.openjfx:javafx-fxml:21.0.1")
    implementation("org.openjfx:javafx-web:21.0.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Apache Commons for compression
    implementation("org.apache.commons:commons-compress:1.25.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")

    // Annotations
    implementation("org.jetbrains:annotations:24.0.1")
}

application {
    mainClass.set("com.dotvault.App")
}

javafx {
    version = "21.0.1"
    modules = listOf(
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.web"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named("run") {
    workingDir = File(".")
}
