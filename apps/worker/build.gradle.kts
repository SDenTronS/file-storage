plugins {
    id("conventions-spring-app")
}

group = "dev.dentron"
version = "0.0.1-SNAPSHOT"
description = "worker"

dependencies {
    implementation(project(":libs:application"))
    implementation(project(":libs:util"))
    implementation(project(":libs:domain"))
    implementation(project(":libs:persistence"))
    runtimeOnly(project(":libs:messaging-kafka"))
    runtimeOnly(project(":libs:storage-s3"))

    implementation(libs.tika.core)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
}
