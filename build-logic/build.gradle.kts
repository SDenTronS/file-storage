plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.springBoot.get()}")
}

gradlePlugin {
    plugins {
        create("conventionsJava") {
            id = "conventions-java"
            implementationClass = "ConventionsJavaPlugin"
        }
        create("conventionsJavaLibrary") {
            id = "conventions-java-library"
            implementationClass = "ConventionsJavaLibraryPlugin"
        }
        create("conventionsLombok") {
            id = "conventions-lombok"
            implementationClass = "ConventionsLombokPlugin"
        }
        create("conventionsSpringApp") {
            id = "conventions-spring-app"
            implementationClass = "ConventionsSpringAppPlugin"
        }
    }
}
