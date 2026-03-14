plugins {
    id("conventions-spring-app")
    `jvm-test-suite`
}

group = "dev.dentron"
version = "0.0.1-SNAPSHOT"
description = "api"

dependencies {
    implementation(project(":libs:application"))
    implementation(project(":libs:util"))
    implementation(project(":libs:domain"))
    implementation(project(":libs:messaging-kafka"))
    runtimeOnly(project(":libs:persistence"))
    runtimeOnly(project(":libs:storage-s3"))

    implementation(libs.auth0.jwt)
    implementation(libs.flyway.core)
    implementation("org.springframework.boot:spring-boot-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}
