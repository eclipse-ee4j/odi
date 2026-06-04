/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi;

import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.invoke.InvokerBuilder;

/**
 * CDI invoker metadata that delegates execution to Micronaut executable methods.
 */
@Internal
public final class OdiExecutableInvokerInfo implements InvokerInfo, Invoker<Object, Object>, InvokerBuilder<InvokerInfo> {
    private final String beanClassName;
    private final String methodDeclaringClassName;
    private final String methodName;
    private final String[] parameterTypeNames;
    private final int[] parameterArrayDimensions;
    private final boolean staticMethod;
    private final boolean[] argumentLookups;

    private boolean instanceLookup;
    private String asyncReturnHandlerClassName;
    private String asyncParameterHandlerClassName;
    private int asyncParameterIndex = -1;

    public OdiExecutableInvokerInfo(String beanClassName,
                                    String methodDeclaringClassName,
                                    String methodName,
                                    String[] parameterTypeNames,
                                    int[] parameterArrayDimensions,
                                    boolean staticMethod) {
        this.beanClassName = beanClassName;
        this.methodDeclaringClassName = methodDeclaringClassName;
        this.methodName = methodName;
        this.parameterTypeNames = parameterTypeNames;
        this.parameterArrayDimensions = parameterArrayDimensions;
        this.staticMethod = staticMethod;
        this.argumentLookups = new boolean[parameterTypeNames.length];
    }

    public String getBeanClassName() {
        return beanClassName;
    }

    public String getMethodDeclaringClassName() {
        return methodDeclaringClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String[] getParameterTypeNames() {
        return parameterTypeNames;
    }

    public int[] getParameterArrayDimensions() {
        return parameterArrayDimensions;
    }

    public boolean isStaticMethod() {
        return staticMethod;
    }

    public boolean isInstanceLookup() {
        return instanceLookup;
    }

    public boolean isArgumentLookup(int index) {
        return argumentLookups[index];
    }

    public String getAsyncReturnHandlerClassName() {
        return asyncReturnHandlerClassName;
    }

    public String getAsyncParameterHandlerClassName() {
        return asyncParameterHandlerClassName;
    }

    public int getAsyncParameterIndex() {
        return asyncParameterIndex;
    }

    public void asyncReturnHandler(String handlerClassName) {
        this.asyncReturnHandlerClassName = handlerClassName;
    }

    public void asyncParameterHandler(String handlerClassName, int parameterIndex) {
        this.asyncParameterHandlerClassName = handlerClassName;
        this.asyncParameterIndex = parameterIndex;
    }

    @Override
    public InvokerBuilder<InvokerInfo> withInstanceLookup() {
        this.instanceLookup = true;
        return this;
    }

    @Override
    public InvokerBuilder<InvokerInfo> withArgumentLookup(int position) {
        if (position < 0 || position >= argumentLookups.length) {
            throw new DeploymentException("Invalid argument lookup position: " + position);
        }
        argumentLookups[position] = true;
        return this;
    }

    @Override
    public InvokerInfo build() {
        return this;
    }

    @Override
    public Object invoke(Object instance, Object[] arguments) throws Exception {
        OdiInvokerExecutor executor = CDI.current().select(OdiInvokerExecutor.class).get();
        return executor.invoke(this, instance, arguments);
    }
}
