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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;

final class MessagesImpl implements Messages {
    private final VisitorContext visitorContext;

    MessagesImpl(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    @Override
    public void info(String message) {
        visitorContext.info(message, null);
    }

    @Override
    public void info(String message, AnnotationTarget relatedTo) {
        Element element = toElement((AnnotationTargetImpl) relatedTo);
        visitorContext.info(
                message,
                element
        );
    }

    private Element toElement(AnnotationTargetImpl relatedTo) {
        AnnotationMetadata annotationMetadata = relatedTo.getAnnotationMetadata();
        return annotationMetadata instanceof Element ? (Element) annotationMetadata : null;
    }

    @Override
    public void info(String message, BeanInfo relatedTo) {
        info(message, relatedTo.declaringClass());
    }

    @Override
    public void info(String message, ObserverInfo relatedTo) {
        info(message, relatedTo.observerMethod());
    }

    @Override
    public void warn(String message) {
        visitorContext.info(message, null);
    }

    @Override
    public void warn(String message, AnnotationTarget relatedTo) {
        Element element = toElement((AnnotationTargetImpl) relatedTo);
        visitorContext.warn(
                message,
                element
        );
    }

    @Override
    public void warn(String message, BeanInfo relatedTo) {
        warn(message, relatedTo.declaringClass());
    }

    @Override
    public void warn(String message, ObserverInfo relatedTo) {
        warn(message, relatedTo.observerMethod());
    }

    @Override
    public void error(String message) {
        visitorContext.fail(message, null);
    }

    @Override
    public void error(String message, AnnotationTarget relatedTo) {
        Element element = toElement((AnnotationTargetImpl) relatedTo);
        visitorContext.fail(
                message,
                element
        );
    }

    @Override
    public void error(String message, BeanInfo relatedTo) {
        error(message, relatedTo.declaringClass());
    }

    @Override
    public void error(String message, ObserverInfo relatedTo) {
        error(message, relatedTo.observerMethod());
    }

    @Override
    public void error(Exception exception) {
        visitorContext.fail("An error occurred: " + exception.getMessage(), null);
    }
}
