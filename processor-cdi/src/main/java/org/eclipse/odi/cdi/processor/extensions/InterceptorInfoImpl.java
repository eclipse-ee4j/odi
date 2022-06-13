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
package org.eclipse.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.interceptor.InterceptorBinding;

class InterceptorInfoImpl extends BeanInfoImpl implements InterceptorInfo {
    private Collection<AnnotationInfo> interceptorBindings;

    InterceptorInfoImpl(BeanElement beanElement,
                        VisitorContext visitorContext) {
        super(beanElement, visitorContext);
    }

    @Override
    public Collection<AnnotationInfo> interceptorBindings() {
        if (interceptorBindings == null) {
            AnnotationMetadata annotationMetadata = beanElement.getAnnotationMetadata();
            interceptorBindings = annotationMetadata.getAnnotationTypesByStereotype(InterceptorBinding.class)
                    .stream().map(ann -> {
                        final AnnotationValue<? extends Annotation> av = annotationMetadata.getAnnotation(ann);
                        return new AnnotationInfoImpl(
                                av
                        );
                    })
                    .collect(Collectors.toSet());
        }
        return interceptorBindings;
    }

    @Override
    public boolean intercepts(InterceptionType interceptionType) {
        if (interceptionType == null) {
            return false;
        } else {
            switch (interceptionType) {
            case POST_ACTIVATE:
            case PRE_PASSIVATE:
            default:
                return false;
            }
        }
    }

    @Override
    public boolean isInterceptor() {
        return true;
    }
}
