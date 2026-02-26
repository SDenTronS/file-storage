import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class ConventionsLombokPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val lombok = libs.findLibrary("lombok").get()

        dependencies {
            add("compileOnly", lombok)
            add("annotationProcessor", lombok)
            add("testCompileOnly", lombok)
            add("testAnnotationProcessor", lombok)
        }
    }
}
