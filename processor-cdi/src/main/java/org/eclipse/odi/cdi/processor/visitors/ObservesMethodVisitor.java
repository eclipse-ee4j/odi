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

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import org.eclipse.odi.cdi.processor.AnnotationUtil;

import java.util.Arrays;

/**
 * The visitor of methods that are declared with {@link Observes}.
 */
public class ObservesMethodVisitor extends ParameterAnnotationInjectableMethodVisitor<Observes> implements TypeElementVisitor<Object, Object> {

    @Override
    protected Class<Observes> getParameterAnnotation() {
        return Observes.class;
    }

    @Override
    public void handleMatch(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context) {
        if (!AnnotationUtil.hasBeanDefiningAnnotation(currentClass)) {
            currentClass.annotate(ApplicationScoped.class);
        }
        methodElement.annotate(AnnotationUtil.ANN_OBSERVES_METHOD, annotationValueBuilder -> {
            AnnotationValue<Observes> observesAnnotation = parameterElement.getAnnotation(Observes.class);
            annotationValueBuilder.member("eventArgumentIndex", Arrays.asList(methodElement.getParameters()).indexOf(parameterElement));
            observesAnnotation.enumValue("notifyObserver", Reception.class).ifPresent(reception -> {
                if (reception == Reception.IF_EXISTS && currentClass.hasStereotype(Dependent.class)) {
                    context.fail("@Dependent beans cannot have Reception.IF_EXISTS event observer.", methodElement);
                    return;
                }

                annotationValueBuilder.member("notifyObserver", reception);
            });
            observesAnnotation.enumValue("during", TransactionPhase.class)
                    .ifPresent(during -> annotationValueBuilder.member("during", during));
            parameterElement.intValue(Priority.class).ifPresent(priority -> annotationValueBuilder.member("priority", priority));
        });
        methodElement.annotate(Executable.class, builder -> builder.member("processOnStartup", true));
    }

}
