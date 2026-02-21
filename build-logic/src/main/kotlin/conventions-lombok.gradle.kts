plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val lombok = libs.findLibrary("lombok").get()

dependencies {
    compileOnly(lombok)
    annotationProcessor(lombok)
    testCompileOnly(lombok)
    testAnnotationProcessor(lombok)
}
