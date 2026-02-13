plugins {
    id("java-library")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":libs:application"))
    implementation(project(":libs:domain"))
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-starter-data-jpa")


    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.proc)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.lombok.mapstruct.binding)
    annotationProcessor(libs.lombok.mapstruct.binding)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Драйвер БД (runtime)
    runtimeOnly(libs.postgresql)

    // Миграции:
    // 1) если ты держишь migration SQL в этом модуле (resources/db/migration),
    //    то зависимость flyway обычно нужна в app-модуле (api), а не тут.
    // 2) если хочешь использовать Flyway API прямо из persistence - тогда включай:
    // implementation(libs.flyway.core)

    // Тесты
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(enforcedPlatform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)

    // (Опционально) если гоняешь интеграционные тесты, часто удобно:
    // testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
