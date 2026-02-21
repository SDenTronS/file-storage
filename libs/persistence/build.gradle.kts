plugins {
    id("conventions-java-library")
    id("conventions-lombok")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:application"))
    implementation(project(":libs:domain"))

    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.proc)

    compileOnly(libs.lombok.mapstruct.binding)
    annotationProcessor(libs.lombok.mapstruct.binding)

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")

    testImplementation(libs.flyway.core)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}
