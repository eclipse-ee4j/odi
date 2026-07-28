plugins {
    id("org.eclipse.odi.build.internal.module")
}

description = "ODI CDI"

dependencies {
    annotationProcessor(project(":micronaut-odi-processor-cdi"))

    api(mn.micronaut.context)
    api(project(":micronaut-odi-core"))
    api(libs.cdi.api)

    compileOnly(mn.micronaut.core.reactive)

    testAnnotationProcessor(project(":micronaut-odi-processor-cdi"))

    testImplementation(libs.javax.annotation.api)
    testImplementation(mn.logback)
    testImplementation(project(":micronaut-odi-test-junit5"))
}
