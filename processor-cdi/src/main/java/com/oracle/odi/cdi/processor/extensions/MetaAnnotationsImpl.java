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
package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

import com.oracle.odi.cdi.processor.transformers.InterceptorBindingTransformer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;

@Internal
final class MetaAnnotationsImpl implements MetaAnnotations {
//    private List<ContextConfig> contextBuilders = new ArrayList<>();
    private final Set<MetaAnnotationImpl> interceptorBindings = new HashSet<>();
    private final Set<MetaAnnotationImpl> qualifiers = new HashSet<>();
    private final Set<MetaAnnotationImpl> stereotypes = new HashSet<>();
    private final VisitorContext visitorContext;

    public MetaAnnotationsImpl(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    @Override
    public ClassConfig addQualifier(Class<? extends Annotation> annotation) {
        final ClassElement classElement = visitorContext.getClassElement(annotation)
                .orElseThrow(() -> new RuntimeException("Qualifier type [" + annotation.getName() + "] must be on the application classpath"));
        classElement.annotate(AnnotationUtil.QUALIFIER);
        final ClassConfigImpl qualifierConfig = new ClassConfigImpl(
                classElement,
                new TypesImpl(visitorContext),
                visitorContext
        );
        qualifiers.add(new MetaAnnotationImpl(qualifierConfig));
        return qualifierConfig;
    }

    @Override
    public ClassConfig addInterceptorBinding(Class<? extends Annotation> annotation) {
        final ClassElement classElement = visitorContext.getClassElement(annotation)
                .orElseThrow(() -> new RuntimeException("InterceptorBinding type [" + annotation.getName() + "] must be on the application classpath"));
        for (AnnotationValue<?> av : InterceptorBindingTransformer.INTERCEPTOR_BINDING_VALUES) {
            classElement.annotate(AnnotationValue.builder(av, RetentionPolicy.RUNTIME).value(annotation).build());
        }
        final ClassConfigImpl interceptorConfig = new ClassConfigImpl(
                classElement,
                new TypesImpl(visitorContext),
                visitorContext
        );
        interceptorBindings.add(new MetaAnnotationImpl(interceptorConfig));
        return interceptorConfig;
    }

    @Override
    public ClassConfig addStereotype(Class<? extends Annotation> annotation) {
        final ClassElement classElement = visitorContext.getClassElement(annotation)
                .orElseThrow(() -> new RuntimeException("Stereotype [" + annotation.getName() + "] must be on the application classpath"));
        classElement.annotate(Stereotype.class);
        classElement.annotate(Bean.class);
        final ClassConfigImpl stereotypeConfig = new ClassConfigImpl(
                classElement,
                new TypesImpl(visitorContext),
                visitorContext
        );
        stereotypes.add(new MetaAnnotationImpl(stereotypeConfig));
        return stereotypeConfig;
    }

    @Override
    public void addContext(
            Class<? extends Annotation> scopeAnnotation,
            Class<? extends AlterableContext> contextClass) {
        // TODO
    }

    @Override
    public void addContext(Class<? extends Annotation> scopeAnnotation,
                           boolean isNormal,
                           Class<? extends AlterableContext> contextClass) {
        // TODO
    }

    public Set<MetaAnnotationImpl> getInterceptorBindings() {
        return interceptorBindings;
    }

    public Set<MetaAnnotationImpl> getQualifiers() {
        return qualifiers;
    }

    public Set<MetaAnnotationImpl> getStereotypes() {
        return stereotypes;
    }

}
