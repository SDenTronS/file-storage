plugins {
    id("conventions-java-library")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:domain"))
    implementation(platform(libs.spring.boot.bom))
    implementation("org.slf4j:slf4j-api")
}
