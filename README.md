# Open DI

Open DI (ODI) is a CDI Lite implementation backed by Micronaut's compile-time dependency injection runtime.

ODI targets CDI Lite rather than the CDI Full runtime extension, decorator, and passivation surface. The implementation uses Micronaut bean definitions, annotation processing, generated proxies, and build-compatible extensions to provide CDI APIs on top of a Micronaut-backed runtime.

The ODI guide is published at <https://eclipse-ee4j.github.io/odi/latest/guide/>.

The CDI Lite TCK passes with CDI Full exclusions only.

The latest CDI Lite TCK compatibility evidence is published at <https://eclipse-ee4j.github.io/odi/tck-results/latest/>. Each CI run is also retained under its immutable workflow run ID, for example `/tck-results/<workflow-run-id>/`.

## Micronaut Core dependency

ODI requires Micronaut Core 5.1.0 or above. The CDI integration hooks used by the ODI processor and runtime were added in [micronaut-projects/micronaut-core#12678](https://github.com/micronaut-projects/micronaut-core/pull/12678), which has been merged.

## Classpath shape

Keep the ODI processor on the annotation processor path and keep the CDI runtime on the application classpath:

```kotlin
dependencies {
    annotationProcessor("org.eclipse.odi:micronaut-odi-processor-cdi:<version>")

    implementation("org.eclipse.odi:micronaut-odi-cdi:<version>")
    implementation("jakarta.cdi:jakarta.cdi-api:5.0.0.Beta1")
}
```

## Useful commands

```bash
./gradlew :docs-examples:cdi-lite-java:test
./gradlew :micronaut-odi-tck-runner:test
./gradlew publishGuide
```
