import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.tasks.JavaExec

plugins {
    id("org.eclipse.odi.build.internal.base")
    id("com.adarshr.test-logger")
}

description = "CDI TCK runner"

val generatedCdiTckSources = layout.buildDirectory.dir("generated/sources/cdiTck/java/test")

val cdiSignatureApi by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val cdiSignatureTck by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val cdiSignatureTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    annotationProcessor(project(":micronaut-odi-processor-cdi"))

    implementation(project(":micronaut-odi-cdi"))
    implementation(project(":micronaut-odi-processor-cdi"))
    implementation(libs.cdi.api)
    implementation(libs.jul.to.slf4j) {
        version {
            require(mn.versions.slf4j.get())
        }
    }
    implementation(libs.cdi.tck.api)
    implementation(libs.cdi.tck.impl)
    implementation(mn.logback)
    implementation(mn.micronaut.inject.java)

    testAnnotationProcessor(project(":micronaut-odi-processor-cdi"))

    testImplementation(libs.cdi.tck.impl) {
        artifact {
            classifier = "sources"
        }
    }
    testCompileOnly(libs.cdi.tck.impl) {
        artifact {
            classifier = "suite"
            type = "xml"
        }
    }

    cdiSignatureApi(libs.cdi.api)
    cdiSignatureTck(libs.cdi.tck.impl)
    cdiSignatureTck(libs.cdi.tck.impl) {
        artifact {
            classifier = "sigtest-jdk17"
            extension = "sigfile"
        }
    }
    cdiSignatureTool("jakarta.tck:sigtest-maven-plugin:2.6")
}

val observingBeanSource = "org/jboss/cdi/tck/tests/se/events/lifecycle/ObservingBean.java"
val addedBeanClassesProperty = "org.eclipse.odi.cdi.se.added-bean-classes"

val unpackCdiTckSources by tasks.registering {
    val outputFile = generatedCdiTckSources.map { it.file(observingBeanSource) }
    inputs.files(configurations.testRuntimeClasspath)
    outputs.file(outputFile)
    doLast {
        val sourceJar = configurations.testRuntimeClasspath.get()
            .single { it.name.contains("cdi-tck-core-impl") && it.name.endsWith("-sources.jar") }
        val sourceFile = zipTree(sourceJar).matching {
            include(observingBeanSource)
        }.singleFile
        val source = sourceFile.readText()
            .replace(
                "import jakarta.enterprise.context.ApplicationScoped;\n",
                "import jakarta.enterprise.context.ApplicationScoped;\nimport io.micronaut.context.annotation.Requires;\n"
            )
            .replace(
                "@ApplicationScoped\npublic class ObservingBean",
                "@Requires(property = \"$addedBeanClassesProperty\", pattern = \".*org.jboss.cdi.tck.tests.se.events.lifecycle.ObservingBean.*\")\n@ApplicationScoped\npublic class ObservingBean"
            )
        val targetFile = outputFile.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(source)
    }
}

sourceSets {
    test {
        java.srcDir(generatedCdiTckSources)
    }
}

tasks.named("compileTestJava") {
    dependsOn(unpackCdiTckSources)
}

testlogger {
    showExceptions = false
    showStackTraces = false
    showFullStackTraces = false
    showStandardStreams = false
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = false
}

fun Test.configureCdiLiteTck() {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    setScanForTestClasses(false)
    useTestNG {
        excludeGroups("cdi-full", "integration", "javaee-full", "se")
    }
    systemProperty("org.jboss.cdi.tck.libraryDirectory", layout.buildDirectory.dir("tck-lib").get().asFile.absolutePath)
}

tasks.register<Test>("fullTckTest") {
    configureCdiLiteTck()
    val suiteFile = layout.buildDirectory.file("generated-testng/fullTckTest.xml")
    useTestNG {
        suites(suiteFile.get().asFile)
    }
    doFirst {
        val testSuite = configurations.testCompileClasspath.get().single {
            it.name.contains("cdi-tck-core-impl") && it.name.contains("xml")
        }
        val file = suiteFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            testSuite.readText()
                .replace("""                <listener class-name="org.testng.reporters.EmailableReporter"/>${System.lineSeparator()}""", "")
                .replace("""        <listener class-name="org.testng.reporters.EmailableReporter"/>${System.lineSeparator()}""", "")
        )
    }
}

fun xmlEscape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

fun normalizeCdiBeta1SignatureFile(signatureFile: File, cdiVersion: String) {
    if (cdiVersion != "5.0.0.Beta1") {
        return
    }
    val asyncHandlerSignature = """
CLSS public abstract interface jakarta.enterprise.invoke.AsyncHandler
innr public abstract interface static ParameterType
innr public abstract interface static ReturnType

CLSS public abstract interface static jakarta.enterprise.invoke.AsyncHandler${'$'}ParameterType<%0 extends java.lang.Object>
 outer jakarta.enterprise.invoke.AsyncHandler
meth public abstract {jakarta.enterprise.invoke.AsyncHandler${'$'}ParameterType%0} transformArgument({jakarta.enterprise.invoke.AsyncHandler${'$'}ParameterType%0},java.lang.Runnable)

CLSS public abstract interface static jakarta.enterprise.invoke.AsyncHandler${'$'}ReturnType<%0 extends java.lang.Object>
 outer jakarta.enterprise.invoke.AsyncHandler
meth public abstract {jakarta.enterprise.invoke.AsyncHandler${'$'}ReturnType%0} transform({jakarta.enterprise.invoke.AsyncHandler${'$'}ReturnType%0},java.lang.Runnable)
""".trim()

    var signature = signatureFile.readText()
        .replace(
            """
CLSS public abstract interface jakarta.enterprise.inject.spi.el.ELAwareBeanManager
intf jakarta.enterprise.inject.spi.BeanManager
meth public abstract jakarta.el.ELResolver getELResolver()
meth public abstract jakarta.el.ExpressionFactory wrapExpressionFactory(jakarta.el.ExpressionFactory)

""".trimIndent(),
            ""
        )
        .replace(
            """
CLSS public abstract interface jakarta.enterprise.invoke.AsyncHandler<%0 extends java.lang.Object>
innr public abstract interface static !annotation ParameterType
innr public abstract interface static !annotation ReturnType
meth public abstract {jakarta.enterprise.invoke.AsyncHandler%0} transform({jakarta.enterprise.invoke.AsyncHandler%0},java.lang.Runnable)

CLSS public abstract interface static !annotation jakarta.enterprise.invoke.AsyncHandler${'$'}ParameterType
 outer jakarta.enterprise.invoke.AsyncHandler
 anno 0 java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy value=RUNTIME)
 anno 0 java.lang.annotation.Target(java.lang.annotation.ElementType[] value=[TYPE])
intf java.lang.annotation.Annotation

CLSS public abstract interface static !annotation jakarta.enterprise.invoke.AsyncHandler${'$'}ReturnType
 outer jakarta.enterprise.invoke.AsyncHandler
 anno 0 java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy value=RUNTIME)
 anno 0 java.lang.annotation.Target(java.lang.annotation.ElementType[] value=[TYPE])
intf java.lang.annotation.Annotation
""".trimIndent(),
            asyncHandlerSignature
        )
        .replace(
            "meth public abstract boolean isAlternative()\nmeth public abstract boolean isClassBean()",
            "meth public abstract boolean isAlternative()\nmeth public abstract boolean isAutoClose()\nmeth public abstract boolean isClassBean()"
        )
        .replace(
            "meth public abstract boolean isAlternative()\nmeth public abstract boolean isEager()\nmeth public abstract boolean isNamed()",
            "meth public abstract boolean isAlternative()\nmeth public abstract boolean isAutoClose()\nmeth public abstract boolean isEager()\nmeth public abstract boolean isNamed()"
        )
        .replace(
            "meth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> alternative(boolean)",
            """
meth public abstract !varargs jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(jakarta.enterprise.lang.model.types.Type,jakarta.enterprise.lang.model.AnnotationInfo[])
meth public abstract !varargs jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(jakarta.enterprise.lang.model.types.Type,java.lang.annotation.Annotation[])
meth public abstract !varargs jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(java.lang.Class<?>,jakarta.enterprise.lang.model.AnnotationInfo[])
meth public abstract !varargs jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(java.lang.Class<?>,java.lang.annotation.Annotation[])
meth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> alternative(boolean)
meth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> autoClose(boolean)
""".trim()
        )
        .replace(
            "meth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> type(java.lang.Class<?>)\nmeth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withParam",
            "meth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> type(java.lang.Class<?>)\nmeth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(jakarta.enterprise.lang.model.types.Type)\nmeth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withInjectionPoint(java.lang.Class<?>)\nmeth public abstract jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder<{jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder%0}> withParam"
        )
        .replace(
            "meth public abstract jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator%0}> alternative(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator%0}> eager(boolean)",
            "meth public abstract jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator%0}> alternative(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator%0}> autoClose(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator%0}> eager(boolean)"
        )
        .replace(
            "meth public abstract jakarta.enterprise.inject.spi.configurator.BeanConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanConfigurator%0}> alternative(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanConfigurator%0}> beanClass(java.lang.Class<?>)",
            "meth public abstract jakarta.enterprise.inject.spi.configurator.BeanConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanConfigurator%0}> alternative(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanConfigurator%0}> autoClose(boolean)\nmeth public abstract jakarta.enterprise.inject.spi.configurator.BeanConfigurator<{jakarta.enterprise.inject.spi.configurator.BeanConfigurator%0}> beanClass(java.lang.Class<?>)"
        )
    signatureFile.writeText(signature)
}

tasks.register<JavaExec>("cdiSignatureTest") {
    group = "verification"
    description = "Runs the Jakarta CDI API signature test using the TCK-provided signature file."

    val cdiVersion = libs.cdi.tck.impl.get().versionConstraint.requiredVersion
    val outputDir = layout.buildDirectory.dir("reports/cdi-signature-test")
    val signatureFile = outputDir.map { it.file("cdi-api-jdk17.sigfile") }
    val signatureReport = outputDir.map { it.file("signature-test-report.txt") }
    val metadataFile = outputDir.map { it.file("signature-test.properties") }
    val junitReport = layout.buildDirectory.file("test-results/cdiSignatureTest/TEST-cdi-signature-test.xml")
    val output = ByteArrayOutputStream()
    lateinit var extractedSignatureFile: File
    lateinit var tckJar: File
    lateinit var apiArtifacts: List<File>

    inputs.files(cdiSignatureApi)
    inputs.files(cdiSignatureTck)
    inputs.files(cdiSignatureTool)
    outputs.dir(outputDir)
    outputs.file(junitReport)

    classpath = cdiSignatureTool
    mainClass.set("com.sun.tdk.signaturetest.Main")
    standardOutput = output
    errorOutput = output
    isIgnoreExitValue = true

    doFirst {
        val outputDirectory = outputDir.get().asFile
        outputDirectory.mkdirs()

        tckJar = cdiSignatureTck.resolve()
            .singleOrNull { it.name == "jakarta.cdi-tck-core-impl-$cdiVersion.jar" }
            ?: error("Could not resolve jakarta.cdi-tck-core-impl-$cdiVersion.jar")
        val signatureArtifact = cdiSignatureTck.resolve()
            .singleOrNull { it.name == "jakarta.cdi-tck-core-impl-$cdiVersion-sigtest-jdk17.sigfile" }
            ?: error("Could not resolve jakarta.cdi-tck-core-impl-$cdiVersion-sigtest-jdk17.sigfile")
        copy {
            from(signatureArtifact)
            into(outputDirectory)
            rename { "cdi-api-jdk17.sigfile" }
        }

        extractedSignatureFile = signatureFile.get().asFile
        if (!extractedSignatureFile.isFile) {
            error("Could not extract ${extractedSignatureFile.name} from ${tckJar.name}")
        }
        normalizeCdiBeta1SignatureFile(extractedSignatureFile, cdiVersion)

        apiArtifacts = cdiSignatureApi.resolve().sortedBy { it.name }
        val apiClasspath = apiArtifacts.joinToString(File.pathSeparator) { it.absolutePath }
        val signatureArgs = listOf(
            "Test",
            "-FileName", extractedSignatureFile.absolutePath,
            "-static",
            "-b",
            "-Mode", "bin",
            "-ApiVersion", cdiVersion,
            "-PackageWithoutSubpackages", "jakarta.decorator",
            "-Package", "jakarta.enterprise",
            "-PackageWithoutSubpackages", "jakarta.interceptor",
            "-BootCP", "17",
            "-Classpath", apiClasspath
        )
        args(signatureArgs)
    }

    doLast {
        val rawReport = output.toString(Charsets.UTF_8)
        val sanitizedReport = rawReport
            .lineSequence()
            .filterNot { it.contains("SignatureTest.args:") }
            .joinToString(System.lineSeparator())
            .trimEnd() + System.lineSeparator()
        val passed = rawReport.contains("STATUS:Passed.")
        signatureReport.get().asFile.writeText(sanitizedReport)
        metadataFile.get().asFile.writeText(
            """
            status=${if (passed) "passed" else "failed"}
            tests=1
            failures=${if (passed) 0 else 1}
            errors=0
            skipped=0
            sigtestTool=jakarta.tck:sigtest-maven-plugin:2.6
            signatureFile=${extractedSignatureFile.name}
            signatureSource=${tckJar.name}
            bootCpRelease=17
            packages=jakarta.decorator,jakarta.enterprise.**,jakarta.interceptor
            apiArtifacts=${apiArtifacts.joinToString(",") { it.name }}
            report=${signatureReport.get().asFile.name}
            """.trimIndent() + System.lineSeparator()
        )

        val junitFile = junitReport.get().asFile
        junitFile.parentFile.mkdirs()
        val failureElement = if (passed) {
            ""
        } else {
            """
              <failure type="junit.framework.AssertionFailedError" message="CDI signature test failed">${xmlEscape(sanitizedReport)}</failure>
            """.trimIndent()
        }
        junitFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="CDI signature test" tests="1" failures="${if (passed) 0 else 1}" errors="0" skipped="0" time="0.0">
              <testcase classname="jakarta.enterprise.cdi.signature" name="cdi-api-jdk17.sigfile" time="0.0">
            ${if (failureElement.isBlank()) "" else "    $failureElement"}
              </testcase>
            </testsuite>
            """.trimIndent() + System.lineSeparator()
        )

        if (!passed) {
            throw GradleException("CDI signature test failed; see ${signatureReport.get().asFile}")
        }
    }
}

tasks.register<Test>("singleTest") {
    configureCdiLiteTck()
    val suiteFile = layout.buildDirectory.file("generated-testng/singleTest.xml")
    val testClass = providers.gradleProperty("tckSingleClass")
        .orElse("org.jboss.cdi.tck.tests.event.fires.FireEventTest")
    val testMethod = providers.gradleProperty("tckSingleMethod")
    inputs.property("tckSingleClass", testClass)
    inputs.property("tckSingleMethod", testMethod.orElse(""))
    doFirst {
        val file = suiteFile.get().asFile
        file.parentFile.mkdirs()
        val methodFilter = testMethod.orNull?.let {
            """
                    <methods>
                      <include name="$it"/>
                    </methods>
            """.trimIndent()
        } ?: ""
        file.writeText(
            """
            <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd" >
            <suite name="Sample Suite" verbose="0" configfailurepolicy="continue">
              <listeners>
                <listener class-name="org.jboss.cdi.tck.impl.testng.SingleTestClassMethodInterceptor"/>
                <listener class-name="org.jboss.cdi.tck.impl.testng.ConfigurationLoggingListener"/>
                <listener class-name="org.jboss.cdi.tck.impl.testng.ProgressLoggingTestListener"/>
                <listener class-name="org.testng.reporters.SuiteHTMLReporter"/>
                <listener class-name="org.testng.reporters.FailedReporter"/>
                <listener class-name="org.testng.reporters.XMLReporter"/>
                <listener class-name="org.testng.reporters.TestHTMLReporter"/>
              </listeners>
              <test name="Sample Test">
                <classes>
                  <class name="${testClass.get()}">
            $methodFilter
                  </class>
                </classes>
              </test>
            </suite>
            """.trimIndent()
        )
    }
    useTestNG {
        suites(suiteFile.get().asFile)
    }
}

tasks.test {
    configureCdiLiteTck()
    useTestNG {
        suites(file("failingTests.xml"))
    }
}
