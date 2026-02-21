plugins {
    id("conventions-java-library")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:domain"))
}
