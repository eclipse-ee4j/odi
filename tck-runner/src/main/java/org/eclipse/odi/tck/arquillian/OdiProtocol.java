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

import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.impl.client.protocol.local.LocalDeploymentPackager;
import org.jboss.arquillian.container.test.impl.execution.event.LocalExecutionEvent;
import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.spi.command.CommandCallback;
import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * ODI protocol implementation.
 *
 */
public class OdiProtocol implements Protocol<OdiProtocolConfiguration> {
    @Inject
    Instance<Injector> injector;

    @Override
    public Class<OdiProtocolConfiguration> getProtocolConfigurationClass() {
        return OdiProtocolConfiguration.class;
    }

    @Override
    public ProtocolDescription getDescription() {
        return new ProtocolDescription("ODI");
    }

    @Override
    public DeploymentPackager getPackager() {
        return new LocalDeploymentPackager();
    }

    @Override
    public ContainerMethodExecutor getExecutor(OdiProtocolConfiguration protocolConfiguration, ProtocolMetaData metaData, CommandCallback callback) {
        return injector.get().inject(new OdiMethodExecutor());
    }

    static class OdiMethodExecutor implements ContainerMethodExecutor {
        @Inject
        Event<LocalExecutionEvent> event;

        @Inject
        Instance<TestResult> testResult;

        @Inject
        Instance<ClassLoader> applicationClassLoader;

        @Override
        public TestResult invoke(TestMethodExecutor testMethodExecutor) {
            event.fire(new LocalExecutionEvent(new TestMethodExecutor() {
                @Override
                public String getMethodName() {
                    return testMethodExecutor.getMethod().getName();
                }

                @Override
                public Method getMethod() {
                    return testMethodExecutor.getMethod();
                }

                @Override
                public Object getInstance() {
                    return OdiDeployableContainer.testInstance;
                }

                @Override
                public void invoke(Object... parameters) {
                    try {
                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        try {
                            Thread.currentThread().setContextClassLoader(applicationClassLoader.get());

                            Object actualTestInstance = OdiDeployableContainer.testInstance;

                            Method actualMethod;
                            try {
                                actualMethod = actualTestInstance.getClass().getMethod(getMethod().getName(),
                                        ClassLoading.convertToTCCL(getMethod().getParameterTypes()));
                            } catch (NoSuchMethodException e) {
                                actualMethod = actualTestInstance.getClass().getDeclaredMethod(getMethod().getName(),
                                        ClassLoading.convertToTCCL(getMethod().getParameterTypes()));
                                actualMethod.setAccessible(true);
                            }

                            try {
                                actualMethod.invoke(actualTestInstance, parameters);
                            } catch (InvocationTargetException e) {
                                Throwable cause = e.getCause();
                                if (cause != null) {
                                    throw cause;
                                } else {
                                    throw e;
                                }
                            }
                        } finally {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    } catch (Throwable e) {
                        rethrowWithCorrectClassloader(e);
                    }
                }
            }));

            return testResult.get();
        }
    }

    public static <T extends Throwable, R> R rethrowWithCorrectClassloader(Throwable t) throws T {
        //clone the exception into the correct class loader
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream a = new ObjectOutputStream(out)) {
            a.writeObject(t);
            a.close();
            t = (T) new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())).readObject();
        } catch (Throwable e) {
            // Ignore
        }
        throw (T) t;
    }
}
