/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi.annotated;

import io.micronaut.context.AbstractBeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.CallableInjectionPoint;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import jakarta.enterprise.inject.spi.Annotated;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;

/**
 * Utils to construct {@link jakarta.enterprise.inject.spi.Annotated}.
 */
public final class OdiAnnotatedUtils {

    public static Annotated asAnnotated(InjectionPoint<?> injectionPoint, Type type) {
        if (injectionPoint instanceof AbstractBeanResolutionContext.FieldSegment) {
            AbstractBeanResolutionContext.FieldSegment fieldSegment = (AbstractBeanResolutionContext.FieldSegment) injectionPoint;
            return new OdiAnnotatedField<>(injectionPoint.getDeclaringBean().getBeanType(),
                    Set.of(type),
                    injectionPoint.getAnnotationMetadata(), fieldSegment.getName());
        }
        if (injectionPoint instanceof FieldInjectionPoint) {
            FieldInjectionPoint fieldInjectionPoint = (FieldInjectionPoint) injectionPoint;
            return new OdiAnnotatedField<>(injectionPoint.getDeclaringBean().getBeanType(),
                    Set.of(type),
                    injectionPoint.getAnnotationMetadata(), fieldInjectionPoint.getName());
        }
        if (injectionPoint instanceof AbstractBeanResolutionContext.MethodArgumentSegment) {
            AbstractBeanResolutionContext.MethodArgumentSegment methodArgumentSegment = (AbstractBeanResolutionContext.MethodArgumentSegment) injectionPoint;
            Argument<?>[] arguments = methodArgumentSegment.getArguments();
            int indexOf = -1;
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].equals(methodArgumentSegment.getArgument())) {
                    indexOf = i;
                    break;
                }
            }

            return new OdiAnnotatedParameter<>(injectionPoint.getDeclaringBean().getBeanType(),
                    Set.of(type),
                    injectionPoint.getAnnotationMetadata(),
                    indexOf,
                    asAnnotatedMethod(injectionPoint, type, methodArgumentSegment)
            );
        }
        if (injectionPoint instanceof AbstractBeanResolutionContext.MethodSegment) {
            AbstractBeanResolutionContext.MethodSegment methodSegment = (AbstractBeanResolutionContext.MethodSegment) injectionPoint;
            return asAnnotatedMethod(injectionPoint, type, methodSegment);
        }
        if (injectionPoint instanceof ArgumentInjectionPoint) {
            CallableInjectionPoint<?> outerInjectionPoint = ((ArgumentInjectionPoint<?, ?>) injectionPoint).getOuterInjectionPoint();
            if (outerInjectionPoint instanceof ConstructorInjectionPoint) {
                ConstructorInjectionPoint constructorInjectionPoint = (ConstructorInjectionPoint) outerInjectionPoint;

                Argument<?>[] arguments = constructorInjectionPoint.getArguments();
                int indexOf = -1;
                for (int i = 0; i < arguments.length; i++) {
                    if (arguments[i].equals(((ArgumentInjectionPoint<?, ?>) injectionPoint).getArgument())) {
                        indexOf = i;
                        break;
                    }
                }

                OdiAnnotatedConstructor<Object> annotatedConstructor = new OdiAnnotatedConstructor<>(injectionPoint.getDeclaringBean().getBeanType(),
                        Set.of(type),
                        injectionPoint.getAnnotationMetadata(),
                        Arrays.stream(constructorInjectionPoint.getArguments()).map(TypeInformation::getType).toArray(Class[]::new),
                        arguments
                );

                return new OdiAnnotatedParameter<>(injectionPoint.getDeclaringBean().getBeanType(),
                        Set.of(type),
                        injectionPoint.getAnnotationMetadata(),
                        indexOf,
                        annotatedConstructor
                );
            }
        }
        return new OdiAnnotated(type, Set.of(type), injectionPoint.getAnnotationMetadata());
    }

    private static OdiAnnotatedMethod<Object> asAnnotatedMethod(InjectionPoint<?> injectionPoint,
                                                                Type type,
                                                                AbstractBeanResolutionContext.MethodSegment methodSegment) {
        return new OdiAnnotatedMethod<>(injectionPoint.getDeclaringBean().getBeanType(),
                Set.of(type),
                injectionPoint.getAnnotationMetadata(),
                methodSegment.getName(),
                Arrays.stream(methodSegment.getArguments()).map(TypeInformation::getType).toArray(Class[]::new),
                methodSegment.getArguments()
        );
    }

}
