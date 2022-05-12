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
package com.oracle.odi.cdi;

import com.oracle.odi.cdi.annotated.OdiAnnotated;
import com.oracle.odi.cdi.annotated.OdiAnnotatedConstructor;
import com.oracle.odi.cdi.annotated.OdiAnnotatedField;
import com.oracle.odi.cdi.annotated.OdiAnnotatedMethod;
import com.oracle.odi.cdi.annotated.OdiAnnotatedParameter;
import io.micronaut.context.AbstractBeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.CallableInjectionPoint;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@Internal
final class OdiInjectionPoint implements InjectionPoint {
    private final OdiBean<?> bean;
    private final Argument<?> argument;
    private final AnnotationMetadata annotationMetadata;
    private final io.micronaut.inject.InjectionPoint<?> injectionPoint;

    OdiInjectionPoint(OdiBean<?> bean,
                      io.micronaut.inject.InjectionPoint<?> injectionPoint,
                      Argument<?> argument) {
        this.bean = Objects.requireNonNull(bean);
        Objects.requireNonNull(injectionPoint);
        this.argument = Objects.requireNonNull(argument);
        this.annotationMetadata = new AnnotationMetadataHierarchy(
                injectionPoint.getAnnotationMetadata(),
                argument.getAnnotationMetadata()
        );
        this.injectionPoint = injectionPoint;
    }

    @Override
    public Type getType() {
        if (argument.getTypeParameters().length == 0) {
            return argument.getType();
        }
        return argument.asParameterizedType();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return AnnotationUtils.synthesizeQualifierAnnotations(annotationMetadata);
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    @Override
    public Member getMember() {
        Annotated annotated = getAnnotated();
        if (annotated instanceof AnnotatedMember) {
            return ((AnnotatedMember<?>) annotated).getJavaMember();
        }
        if (annotated instanceof AnnotatedParameter) {
            return ((AnnotatedParameter<?>) annotated).getDeclaringCallable().getJavaMember();
        }
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Annotated getAnnotated() {
        if (injectionPoint instanceof AbstractBeanResolutionContext.FieldSegment) {
            AbstractBeanResolutionContext.FieldSegment fieldSegment = (AbstractBeanResolutionContext.FieldSegment) injectionPoint;
            return new OdiAnnotatedField<>(injectionPoint.getDeclaringBean().getBeanType(),
                    Set.of(getType()),
                    injectionPoint.getAnnotationMetadata(), fieldSegment.getName());
        }
        if (injectionPoint instanceof FieldInjectionPoint) {
            FieldInjectionPoint fieldInjectionPoint = (FieldInjectionPoint) injectionPoint;
            return new OdiAnnotatedField<>(injectionPoint.getDeclaringBean().getBeanType(),
                    Set.of(getType()),
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
                    Set.of(getType()),
                    injectionPoint.getAnnotationMetadata(),
                    indexOf,
                    asAnnotatedMethod(methodArgumentSegment)
            );
        }
        if (injectionPoint instanceof AbstractBeanResolutionContext.MethodSegment) {
            AbstractBeanResolutionContext.MethodSegment methodSegment = (AbstractBeanResolutionContext.MethodSegment) injectionPoint;
            return asAnnotatedMethod(methodSegment);
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
                        Set.of(getType()),
                        injectionPoint.getAnnotationMetadata(),
                        Arrays.stream(constructorInjectionPoint.getArguments()).map(TypeInformation::getType).toArray(Class[]::new),
                        arguments
                );

                return new OdiAnnotatedParameter<>(injectionPoint.getDeclaringBean().getBeanType(),
                        Set.of(getType()),
                        injectionPoint.getAnnotationMetadata(),
                        indexOf,
                        annotatedConstructor
                );
            }
        }
        return new OdiAnnotated(
                getType(),
                Set.of(getType()),
                annotationMetadata
        );
    }

    private OdiAnnotatedMethod<Object> asAnnotatedMethod(AbstractBeanResolutionContext.MethodSegment methodSegment) {
        return new OdiAnnotatedMethod<>(injectionPoint.getDeclaringBean().getBeanType(),
                Set.of(getType()),
                injectionPoint.getAnnotationMetadata(),
                methodSegment.getName(),
                Arrays.stream(methodSegment.getArguments()).map(TypeInformation::getType).toArray(Class[]::new),
                methodSegment.getArguments()
        );
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return Modifier.isTransient(getMember().getModifiers());
    }
}
