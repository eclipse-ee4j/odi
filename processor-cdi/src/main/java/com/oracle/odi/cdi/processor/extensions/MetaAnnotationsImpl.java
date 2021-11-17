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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;

@Internal
final class MetaAnnotationsImpl implements MetaAnnotations {
//    private List<ContextConfig> contextBuilders = new ArrayList<>();
    private final List<AnnotationClassConfig<?>> interceptorBindings = new ArrayList<>();
    private final List<AnnotationClassConfig<?>> qualifiers = new ArrayList<>();
    private final List<AnnotationClassConfig<?>> stereotypes = new ArrayList<>();

    @Override
    public void addQualifier(Class<? extends Annotation> annotation, Consumer<ClassConfig> config) {
        registerMapper(annotation, config, this.qualifiers);
    }

    @Override
    public void addInterceptorBinding(Class<? extends Annotation> annotation, Consumer<ClassConfig> config) {
        registerMapper(annotation, config, this.interceptorBindings);
    }

    @Override
    public void addStereotype(Class<? extends Annotation> annotation, Consumer<ClassConfig> config) {
        registerMapper(annotation, config, this.stereotypes);
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

    public List<AnnotationClassConfig<?>> getInterceptorBindings() {
        return interceptorBindings;
    }

    public List<AnnotationClassConfig<?>> getQualifiers() {
        return qualifiers;
    }

    public List<AnnotationClassConfig<?>> getStereotypes() {
        return stereotypes;
    }

    private void registerMapper(Class<? extends Annotation> annotation,
                                Consumer<ClassConfig> config,
                                List<MetaAnnotationsImpl.AnnotationClassConfig<?>> list) {
        final AnnotationClassConfig<Annotation> mapper =
                new AnnotationClassConfig<>() {

                    @Override
                    public void accept(ClassConfig classConfig) {
                        config.accept(classConfig);
                    }

                    @Override
                    public Class<Annotation> annotationType() {
                        return (Class<Annotation>) annotation;
                    }
                };

        list.add(mapper);
    }

    public interface AnnotationClassConfig<T extends Annotation> extends Consumer<ClassConfig> {
        Class<T> annotationType();
    }
}
