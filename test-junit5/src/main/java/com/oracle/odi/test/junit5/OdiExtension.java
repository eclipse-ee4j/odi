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
package com.oracle.odi.test.junit5;

import java.lang.reflect.Executable;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;

/**
 * JUnit5 Extension.
 */
public class OdiExtension implements AfterAllCallback, TestInstanceFactory, ParameterResolver {
    private SeContainer seContainer;

    @Override
    public void afterAll(ExtensionContext context) {
        if (seContainer != null) {
            seContainer.close();
            this.seContainer = null;
        }
    }

    @Override
    public Object createTestInstance(
            TestInstanceFactoryContext factoryContext,
            ExtensionContext extensionContext) throws TestInstantiationException {
        if (seContainer == null) {
            this.seContainer = SeContainerInitializer.newInstance()
                    .initialize();
        }
        final Class<?> testClass = factoryContext.getTestClass();
        return seContainer.select(testClass).get();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (seContainer instanceof ApplicationContextProvider) {
            ApplicationContext applicationContext = ((ApplicationContextProvider) seContainer).getApplicationContext();
            return getArgument(parameterContext, applicationContext) != null;

        }
        return false;
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) throws ParameterResolutionException {

        ApplicationContext applicationContext = ((ApplicationContextProvider) seContainer).getApplicationContext();
        final Argument<?> argument = getArgument(parameterContext, applicationContext);
        if (argument == null) {
            throw new ParameterResolutionException("Cannot resolve parameter: " + parameterContext.getParameter());
        }
        return applicationContext.getBean(argument, Qualifiers.forArgument(argument));
    }

    private Argument<?> getArgument(ParameterContext parameterContext, ApplicationContext applicationContext) {
        try {
            final Executable declaringExecutable = parameterContext.getDeclaringExecutable();
            final ExecutableMethod<?, Object> executableMethod = applicationContext.getExecutableMethod(
                    declaringExecutable.getDeclaringClass(),
                    declaringExecutable.getName(),
                    declaringExecutable.getParameterTypes()
            );
            final Argument<?>[] arguments = executableMethod.getArguments();
            return arguments[parameterContext.getIndex()];
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
