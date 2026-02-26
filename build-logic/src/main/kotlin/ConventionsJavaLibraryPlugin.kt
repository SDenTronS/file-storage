import org.gradle.api.Plugin
import org.gradle.api.Project

class ConventionsJavaLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")
        pluginManager.apply("conventions-java")
    }
}
