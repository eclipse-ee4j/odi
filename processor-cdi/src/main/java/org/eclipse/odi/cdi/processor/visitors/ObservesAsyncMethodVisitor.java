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
package org.eclipse.odi.cdi.processor.visitors;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import org.eclipse.odi.cdi.processor.AnnotationUtil;

import java.util.Arrays;

/**
 * The visitor of methods that are declared with {@link ObservesAsync}.
 */
public class ObservesAsyncMethodVisitor extends ParameterAnnotationInjectableMethodVisitor<ObservesAsync> implements TypeElementVisitor<Object, Object> {

    @Override
    protected Class<ObservesAsync> getParameterAnnotation() {
        return ObservesAsync.class;
    }

    @Override
    public void handleMatch(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context) {
        if (!AnnotationUtil.hasBeanDefiningAnnotation(currentClass)) {
            currentClass.annotate(ApplicationScoped.class);
        }
        methodElement.annotate(AnnotationUtil.ANN_OBSERVES_METHOD, annotationValueBuilder -> {
            annotationValueBuilder.member("eventArgumentIndex", Arrays.asList(methodElement.getParameters()).indexOf(parameterElement));
            AnnotationValue<ObservesAsync> observesAnnotation = parameterElement.getAnnotation(ObservesAsync.class);
            annotationValueBuilder.member("eventArgumentIndex", Arrays.asList(methodElement.getParameters()).indexOf(parameterElement));
            annotationValueBuilder.member("async", true);
            observesAnnotation.enumValue("notifyObserver", Reception.class)
                    .ifPresent(reception -> annotationValueBuilder.member("notifyObserver", reception));
            parameterElement.intValue(Priority.class).ifPresent(priority -> annotationValueBuilder.member("priority", priority));
        });
    }

}
