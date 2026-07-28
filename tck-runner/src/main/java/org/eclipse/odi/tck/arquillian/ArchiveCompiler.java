/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.odi.tck.arquillian;

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.MixinVisitorProcessor;
import io.micronaut.annotation.processing.PackageElementVisitorProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.service.SoftServiceLoader;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import org.eclipse.odi.cdi.processor.CdiUtil;
import org.eclipse.odi.cdi.processor.extensions.BuildTimeExtensionRegistry;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

/**
 * IMPORTANT: assumes that it is possible to iterate through the {@code Archive}
 * and for each {@code .class} file in there, find a corresponding {@code .java}
 * file in this class's classloader. In other words, the CDI TCK source JAR must
 * be on classpath.
 */
final class ArchiveCompiler {
    private static final String BEAN_PACKAGES_OPTION = "micronaut.cdi.bean.packages";
    private static final Set<String> CDI_PROCESSOR_OPTIONS = Set.of(
            CdiUtil.BEAN_CLASSES_OPTION,
            CdiUtil.BUILD_COMPATIBLE_EXTENSIONS_OPTION,
            BEAN_PACKAGES_OPTION
    );

    private final DeploymentDir deploymentDir;
    private final Archive<?> deploymentArchive;

    ArchiveCompiler(DeploymentDir deploymentDir, Archive<?> deploymentArchive) {
        this.deploymentDir = deploymentDir;
        this.deploymentArchive = deploymentArchive;
    }

    void compile() throws ArchiveCompilationException, ArchiveCompilerException {
        try {
            if (deploymentArchive instanceof WebArchive) {
                compileWar();
            } else if (ClassPath.isRepresentedBy(deploymentArchive)) {
                compileClassPath();
            } else {
                throw new ArchiveCompilerException("Unknown archive type: " + deploymentArchive);
            }
        } catch (IOException e) {
            throw new ArchiveCompilerException("Compilation failed", e);
        }
    }

    private void compileWar() throws ArchiveCompilationException, ArchiveCompilerException, IOException {
        List<File> sourceFiles = new ArrayList<>();
        Set<String> deploymentClassNames = new LinkedHashSet<>();
        for (Map.Entry<ArchivePath, Node> entry : deploymentArchive.getContent().entrySet()) {
            String path = entry.getKey().get();
            if (path.startsWith("/WEB-INF/classes") && path.endsWith(".class")) {
                addSourceFile(
                        path.replace("/WEB-INF/classes/", ""),
                        true,
                        sourceFiles,
                        deploymentClassNames
                );
            } else if (path.startsWith("/WEB-INF/classes") && entry.getValue().getAsset() != null) {
                copyClassResource(path, entry.getValue());
            } else if (path.startsWith("/WEB-INF/lib") && path.endsWith(".jar")) {
                String jarFile = path.replace("/WEB-INF/lib", "");
                Path jarFilePath = deploymentDir.lib.resolve(jarFile.substring(1)); // jarFile begins with `/`

                Files.createDirectories(jarFilePath.getParent()); // make sure the directory exists
                try (InputStream in = entry.getValue().getAsset().openStream()) {
                    Files.copy(in, jarFilePath);
                }
                addSourceFilesFromJar(jarFilePath, sourceFiles, deploymentClassNames);
            }
        }

        doCompile(sourceFiles, deploymentClassNames, deploymentDir.target.toFile());
        setupCdiProviderService();
    }

    private void copyClassResource(String path, Node node) throws IOException {
        String resource = path.replace("/WEB-INF/classes/", "");
        Path resourcePath = deploymentDir.target.resolve(resource);
        Files.createDirectories(resourcePath.getParent());
        try (InputStream in = node.getAsset().openStream()) {
            Files.copy(in, resourcePath);
        }
    }

    private void compileClassPath() throws ArchiveCompilationException, ArchiveCompilerException, IOException {
        List<File> sourceFiles = new ArrayList<>();
        Set<String> deploymentClassNames = new LinkedHashSet<>();
        for (Map.Entry<ArchivePath, Node> entry : deploymentArchive.getContent().entrySet()) {
            String path = entry.getKey().get();
            Node node = entry.getValue();
            if (path.endsWith(".jar") && node.getAsset() != null) {
                String jarFile = path.startsWith("/") ? path.substring(1) : path;
                Path jarFilePath = deploymentDir.lib.resolve(jarFile);

                Files.createDirectories(jarFilePath.getParent());
                try (InputStream in = node.getAsset().openStream()) {
                    Files.copy(in, jarFilePath);
                }
                addSourceFilesFromJar(jarFilePath, sourceFiles, deploymentClassNames);
            }
        }

        doCompile(sourceFiles, deploymentClassNames, deploymentDir.target.toFile());
        setupCdiProviderService();
    }

    private void addSourceFilesFromJar(Path jarFilePath,
                                       List<File> sourceFiles,
                                       Set<String> deploymentClassNames) throws IOException, ArchiveCompilerException {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarFilePath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".class")) {
                    addSourceFile(jarEntry.getName(), false, sourceFiles, deploymentClassNames);
                }
            }
        }
    }

    private void addSourceFile(String classFile,
                               boolean required,
                               List<File> sourceFiles,
                               Set<String> deploymentClassNames) throws IOException, ArchiveCompilerException {
        String sourceFile = "/" + classFile.replace(".class", ".java");
        String className = classFile.replace(".class", "").replace('/', '.');
        if (required) {
            deploymentClassNames.add(className);
        }
        if (sourceFile.contains("$") && !sourceFile.endsWith("$Dollar.java")) {
            // skip nested classes
            //
            // special case for $Dollar, which is the only class in CDI TCK
            // whose name actually intentionally contains '$'
            //
            // this is crude, maybe there's a better way?
            return;
        }
        try (InputStream in = ArchiveCompiler.class.getResourceAsStream(sourceFile)) {
            if (in == null) {
                if (required) {
                    throw new ArchiveCompilerException("Source file not found: " + sourceFile);
                }
                return;
            }
            if (!required && !deploymentClassNames.add(className)) {
                return;
            }
            Path sourceFilePath = deploymentDir.source.resolve(sourceFile.substring(1)); // sourceFile begins with `/`
            sourceFiles.add(sourceFilePath.toFile());

            Files.createDirectories(sourceFilePath.getParent()); // make sure the directory exists
            Files.copy(in, sourceFilePath);
        }
    }

    private void doCompile(Collection<File> testSources,
                           Collection<String> deploymentClassNames,
                           File outputDir) throws ArchiveCompilationException, IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final Map<ArchivePath, Node> extension = deploymentArchive.getContent(object ->
            object.get().contains("jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension")
        );
        boolean hasBuildExtensions = !extension.isEmpty();
        boolean hasAsyncHandlers = !deploymentArchive.getContent(object ->
                object.get().contains("jakarta.enterprise.invoke.AsyncHandler$ReturnType")
                        || object.get().contains("jakarta.enterprise.invoke.AsyncHandler$ParameterType")
                        || object.get().contains("jakarta.enterprise.invoke.AsyncHandler.ReturnType")
                        || object.get().contains("jakarta.enterprise.invoke.AsyncHandler.ParameterType")
        ).isEmpty();
        try (StandardJavaFileManager mgr = compiler.getStandardFileManager(diagnostics, null, null)) {
            final String targetDir = outputDir.getAbsolutePath();
            List<String> args = new ArrayList<>(compileOptions(targetDir, deploymentClassNames));
            JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, diagnostics,
                                                                 args, null, mgr.getJavaFileObjectsFromFiles(testSources));
            if (hasBuildExtensions) {
                // run without processors since extensions have to be applied on the compiled code
                task.setProcessors(Collections.emptyList());
            } else {
                if (hasAsyncHandlers) {
                    ClassLoader classLoader = new DeploymentClassLoader(deploymentDir);
                    BuildTimeExtensionRegistry.setInstance(new BuildTimeExtensionRegistry() {
                        @Override
                        protected SoftServiceLoader<?> findAsyncHandlers(Class<?> handlerType) {
                            return SoftServiceLoader.load(handlerType, classLoader);
                        }
                    });
                }
                task.setProcessors(getAnnotationProcessors());
            }
            Boolean success;
            try {
                success = callTask(task, args);
            } finally {
                if (hasAsyncHandlers && !hasBuildExtensions) {
                    BuildTimeExtensionRegistry.setInstance(null);
                }
            }
            if (!Boolean.TRUE.equals(success)) {
                outputDiagnostics(diagnostics);
            } else if (hasBuildExtensions) {
                // now run another task that produces the beans
                final Map.Entry<ArchivePath, Node> extensionEntry = extension.entrySet().iterator().next();
                final Node extensionValue = extensionEntry.getValue();
                final String extensionName = IOUtils.readText(
                        new BufferedReader(new InputStreamReader(extensionValue.getAsset().openStream()))
                );
                String packageName = extensionName.substring(0, extensionName.lastIndexOf('.'));
                args = new ArrayList<>(compileOptions(targetDir, deploymentClassNames));
                args.add("-A" + CdiUtil.BUILD_COMPATIBLE_EXTENSIONS_OPTION + "=true");
                if (deploymentArchive.contains("/WEB-INF/beans.xml")) {
                    args.add("-A" + BEAN_PACKAGES_OPTION + "=" + packageName);
                }

                final Path applicationSource = setupExtensionCompilation(extensionName, packageName);
                final JavaCompiler.CompilationTask enhancementTask = compiler.getTask(
                        null,
                        mgr,
                        diagnostics,
                        args,
                        null,
                        mgr.getJavaFileObjectsFromFiles(Collections.singleton(applicationSource.toFile()))
                );

                ClassLoader classLoader = new DeploymentClassLoader(deploymentDir);
                SoftServiceLoader<BuildCompatibleExtension> buildExtensionLoader =
                        SoftServiceLoader.load(BuildCompatibleExtension.class, classLoader);
                BuildTimeExtensionRegistry.setInstance(new BuildTimeExtensionRegistry() {
                    @Override
                    protected SoftServiceLoader<BuildCompatibleExtension> findExtensions() {
                        return buildExtensionLoader;
                    }

                    @Override
                    protected SoftServiceLoader<?> findAsyncHandlers(Class<?> handlerType) {
                        return SoftServiceLoader.load(handlerType, classLoader);
                    }
                });
                try {
                    enhancementTask.setProcessors(getAnnotationProcessors());
                    if (!Boolean.TRUE.equals(callTask(enhancementTask, args))) {
                        outputDiagnostics(diagnostics);
                    }
                } finally {
                    BuildTimeExtensionRegistry.setInstance(null);
                }
            }
        }
    }

    private static Boolean callTask(JavaCompiler.CompilationTask task, List<String> args) {
        Map<String, String> previousOptions = new LinkedHashMap<>(CDI_PROCESSOR_OPTIONS.size());
        for (String option : CDI_PROCESSOR_OPTIONS) {
            previousOptions.put(option, System.getProperty(option));
            System.clearProperty(option);
        }
        // Micronaut exposes micronaut.* processor options through system properties
        // as well as javac options. Keep this in-process TCK compiler task-local.
        for (Map.Entry<String, String> option : processorOptions(args).entrySet()) {
            if (CDI_PROCESSOR_OPTIONS.contains(option.getKey())) {
                System.setProperty(option.getKey(), option.getValue());
            }
        }
        try {
            return task.call();
        } finally {
            for (Map.Entry<String, String> option : previousOptions.entrySet()) {
                if (option.getValue() == null) {
                    System.clearProperty(option.getKey());
                } else {
                    System.setProperty(option.getKey(), option.getValue());
                }
            }
        }
    }

    private static Map<String, String> processorOptions(List<String> args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg.startsWith("-A")) {
                int separator = arg.indexOf('=');
                String key = separator > 2 ? arg.substring(2, separator) : arg.substring(2);
                String value = separator > 2 ? arg.substring(separator + 1) : "";
                options.put(key, value);
            }
        }
        return options;
    }

    private static List<String> compileOptions(String targetDir, Collection<String> deploymentClassNames) {
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(targetDir);
        args.add("-classpath");
        args.add(targetDir + File.pathSeparator + System.getProperty("java.class.path"));
        args.add("-verbose");
        if (!deploymentClassNames.isEmpty()) {
            args.add("-A" + CdiUtil.BEAN_CLASSES_OPTION + "=" + String.join(",", deploymentClassNames));
        }
        return args;
    }

    private Path setupExtensionCompilation(String extensionName, String packageName) throws IOException {

        final Path applicationSource = deploymentDir.target.resolve(
                (packageName + ".Application").replace('.', '/') + ".java"
        );
        String sourceCode = "package " + packageName + ";\n" +
        "@org.eclipse.odi.cdi.annotation.OdiApplication class Application {}";
        Files.write(
                applicationSource,
                sourceCode.getBytes(
                        StandardCharsets.UTF_8)
        );
        final Path extensionServiceEntry = deploymentDir.target.resolve(
                "META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension");
        Files.createDirectories(extensionServiceEntry.getParent());
        Files.write(extensionServiceEntry, extensionName.getBytes(StandardCharsets.UTF_8));
        return applicationSource;
    }

    private void setupCdiProviderService() throws IOException {
        final Path providerServiceEntry = deploymentDir.target.resolve(
                "META-INF/services/jakarta.enterprise.inject.spi.CDIProvider");
        Files.createDirectories(providerServiceEntry.getParent());
        Files.write(providerServiceEntry, "org.eclipse.odi.cdi.CDIProviderImpl".getBytes(StandardCharsets.UTF_8));
    }

    private void outputDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) throws ArchiveCompilationException {
        throw new ArchiveCompilationException("Compilation failed:\n" + diagnostics.getDiagnostics()
                .stream()
                .map(it -> {
                    if (it.getSource() == null) {
                        return "- " + it.getMessage(Locale.US);
                    }
                    Path source = deploymentDir.source.relativize(Paths.get(it.getSource().toUri().getPath()));
                    return "- " + source + ":" + it.getLineNumber() + " " + it.getMessage(Locale.US);
                })
                .collect(Collectors.joining("\n")));
    }

    private List<Processor> getAnnotationProcessors() {
        List<Processor> result = new ArrayList<>();
        result.add(new MixinVisitorProcessor());
        result.add(new PackageElementVisitorProcessor());
        result.add(new TypeElementVisitorProcessor());
        result.add(new AggregatingTypeElementVisitorProcessor());
        result.add(new BeanDefinitionInjectProcessor());
        return result;
    }

}
