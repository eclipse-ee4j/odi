package com.oracle.odi.cdi.processor.extensions;

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
