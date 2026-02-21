plugins {
    id("conventions-java-library")
    id("conventions-lombok")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:application"))

    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-kafka")
}
