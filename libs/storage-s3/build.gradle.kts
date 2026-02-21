plugins {
    id("conventions-java-library")
    id("conventions-lombok")
}

group = "dev.dentron"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":libs:application"))
    implementation(project(":libs:util"))

    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.minio)
    implementation(libs.aws.sdk.s3)
}
