import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType

class ConventionsJavaPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        repositories {
            mavenCentral()
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
            add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
            add("testImplementation", libs.findLibrary("junit-jupiter").get())
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            workingDir = rootDir
        }
    }
}
