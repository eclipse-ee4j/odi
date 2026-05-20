# ODI CDI Lite TCK Results - JDK 25

Generated: 2026-05-20T14:01:33Z

## Scope

ODI is validating CDI Lite compatibility only. This evidence does not claim CDI Full compatibility.

ODI currently targets Java 25+. This evidence was collected on JDK 25; no Java 17 or Java 21 evidence is claimed.

Excluded TestNG groups: `cdi-full`, `integration`, `javaee-full`, `se`.

## Test Results

- Tests: 775
- Failures: 0
- Errors: 0
- Skipped: 0

## Product

- Organization: Oracle
- Product: Open DI (ODI)
- Version: 1.0.0-SNAPSHOT
- Repository: https://github.com/eclipse-ee4j/odi
- ODI commit: 9226a00951a8f2411b629fb9d03f2f058ee08889
- Included Micronaut Core commit: 67e61daef88d0eb596696d67fee8573330f32140

## Specification And TCK

- Specification: Jakarta Contexts Dependency Injection 4.1
- Specification URL: https://jakarta.ee/specifications/cdi/4.1/
- TCK: Jakarta CDI TCK 4.1.0
- TCK download: https://download.eclipse.org/ee4j/cdi/4.1/cdi-tck-4.1.0-dist.zip
- TCK SHA-256: 446029ee1ce694d2a9ae8893d16be7afd7e1c0ed8705064b7095af174cf97ea0

## Additional Certification Requirements

- Signature-test evidence: passed

## Signature Test

- Status: passed
- Tests: 1
- Failures: 0
- Errors: 0
- Skipped: 0
- Tool: jakarta.tck:sigtest-maven-plugin:2.6
- TCK signature file: cdi-api-jdk17.sig
- Signature source: cdi-tck-core-impl-4.1.0.jar
- Boot class path release: 17
- Checked packages: jakarta.decorator,jakarta.enterprise.**,jakarta.interceptor
- API artifacts: jakarta.annotation-api-3.0.0.jar,jakarta.el-api-6.0.0.jar,jakarta.enterprise.cdi-api-4.1.0.jar,jakarta.enterprise.lang-model-4.1.0.jar,jakarta.inject-api-2.0.1.jar,jakarta.interceptor-api-2.2.0.jar
- Report: ./signature-test/signature-test-report.txt

The signature file is extracted dynamically from the resolved Jakarta CDI TCK artifact during the CI run.

## Environment

- Workflow run: https://github.com/eclipse-ee4j/odi/actions/runs/26167002801
- Runner OS: Linux
- Runner architecture: X64
- Java version: 25

```
java version "25.0.3" 2026-04-21 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 25.0.3+9.1 (build 25.0.3+9-LTS-jvmci-b01)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 25.0.3+9.1 (build 25.0.3+9-LTS-jvmci-b01, mixed mode, sharing)
```

## Artifacts

- Sanitized JUnit XML: ./junit-xml/
- Signature-test evidence: ./signature-test/

Raw Gradle console output and unsanitized Gradle reports are intentionally not published because the build runs with secret-backed environment variables.
