plugins {
    id("conventions-java-library")
    id("conventions-lombok")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:domain"))
    implementation(project(":libs:util"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.jackson.databind)
    implementation("org.springframework.boot:spring-boot-data-jpa")
    implementation("org.springframework.boot:spring-boot-security")
    implementation(libs.uuid.creator)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}
