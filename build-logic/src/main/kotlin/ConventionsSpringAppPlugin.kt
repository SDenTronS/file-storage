import org.gradle.api.Plugin
import org.gradle.api.Project

class ConventionsSpringAppPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("org.springframework.boot")
        pluginManager.apply("io.spring.dependency-management")
        pluginManager.apply("conventions-java")
        pluginManager.apply("conventions-lombok")
    }
}
