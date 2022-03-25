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
package com.oracle.odi.tck.arquillian;

import com.oracle.odi.cdi.OdiApplicationContextBuilder;
import com.oracle.odi.tck.porting.BeansImpl;
import io.micronaut.context.ApplicationContext;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.context.annotation.DeploymentScoped;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.container.LibraryContainer;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * ODI deployable container.
 */
public class OdiDeployableContainer implements DeployableContainer<OdiContainerConfiguration> {

    static Object testInstance;
    static ClassLoader old;

    private static final Logger LOGGER = LoggerFactory.getLogger(OdiDeployableContainer.class);

    @Inject
    @DeploymentScoped
    private InstanceProducer<ApplicationContext> runningApplicationContext;

    @Inject
    @DeploymentScoped
    private InstanceProducer<ClassLoader> applicationClassLoader;

    @Inject
    @DeploymentScoped
    private InstanceProducer<DeploymentDir> deploymentDir;

    @Inject
    private Instance<TestClass> testClass;

    @Override
    public Class<OdiContainerConfiguration> getConfigurationClass() {
        return OdiContainerConfiguration.class;
    }

    @Override
    public void setup(OdiContainerConfiguration configuration) {
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("ODI");
    }

    private static JavaArchive buildSupportLibrary() {
        JavaArchive supportLib = ShrinkWrap.create(JavaArchive.class, "odi-tck-support.jar")
                .addPackage(BeansImpl.class.getPackage());
        return supportLib;
    }


    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        if (archive instanceof LibraryContainer) {
            ((LibraryContainer<?>) archive).addAsLibrary(buildSupportLibrary());
        } else {
            throw new IllegalStateException("Expected library container!");
        }
        old = Thread.currentThread().getContextClassLoader();
        if (testClass.get() == null) {
            throw new IllegalStateException("Test class not available");
        }
        Class testJavaClass = testClass.get().getJavaClass();


        try {
            DeploymentDir deploymentDir = new DeploymentDir();
            this.deploymentDir.set(deploymentDir);

            new ArchiveCompiler(deploymentDir, archive).compile();

            ClassLoader classLoader = new DeploymentClassLoader(deploymentDir);
            applicationClassLoader.set(classLoader);

            ApplicationContext applicationContext = new OdiApplicationContextBuilder()
                    .classLoader(classLoader)
                    .build()
                    .start();

            runningApplicationContext.set(applicationContext);
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> actualTestClass = Class.forName(testJavaClass.getName(), true, classLoader);
            testInstance = actualTestClass.newInstance();
            // maybe there's a better way? Quarkus makes the test class a bean and then looks it up from CDI
            OdiInjectionEnricher.enrich(testInstance, applicationContext);
        } catch (Throwable t) {
            //clone the exception into the correct class loader
            Throwable nt;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ObjectOutputStream a = new ObjectOutputStream(out)) {
                a.writeObject(t);
                a.close();
                nt = (Throwable) new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())).readObject();
            } catch (Exception e) {
                throw new DeploymentException("Unable to start the application context", t);
            }
            throw new DeploymentException("Unable to start the application context", nt);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        return new ProtocolMetaData();
    }

    @Override
    public void undeploy(Archive<?> archive) {
        try {
            ApplicationContext appContext = runningApplicationContext.get();
            if (appContext != null) {
                Thread.currentThread().setContextClassLoader(runningApplicationContext.get().getClassLoader());
                appContext.stop();
            }
            testInstance = null;

            DeploymentDir deploymentDir = this.deploymentDir.get();
            if (deploymentDir != null) {
                deleteDirectory(deploymentDir.root);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    private static void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Unable to delete directory: " + dir, e);
        }
    }

    @Override
    public void deploy(Descriptor descriptor) {
        throw new UnsupportedOperationException("Container does not support deployment of Descriptors");

    }

    @Override
    public void undeploy(Descriptor descriptor) {
        throw new UnsupportedOperationException("Container does not support deployment of Descriptors");

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
