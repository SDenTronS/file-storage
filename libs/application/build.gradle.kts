plugins {
    id("java-library")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-data-jpa")
    implementation("org.springframework.boot:spring-boot-security")

    implementation(project(":libs:domain"))
    implementation(project(":libs:common"))
    implementation("com.github.f4b6a3:uuid-creator:6.1.0")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}