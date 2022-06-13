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

import java.util.ArrayList;
import java.util.List;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import jakarta.enterprise.util.Nonbinding;

/**
 * Models a meta annotation in CDI terms at build time.
 *
 */
public class MetaAnnotationImpl {
    private final ClassConfigImpl classConfig;

    public MetaAnnotationImpl(ClassConfigImpl classConfig) {
        this.classConfig = classConfig;
    }

    /**
     * @return The class config.
     */
    public ClassConfigImpl getClassConfig() {
        return classConfig;
    }

    /**
     * @return The non-binding members.
     */
    public String[] getNonBindingMembers() {
        List<String> nonBinding = new ArrayList<>(10);
        final ElementQuery<MethodElement> query =
                ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared();
        classConfig.getElement()
                   .getEnclosedElements(query
        )
                .forEach(m -> {
                    if (m.hasAnnotation(Nonbinding.class)) {
                        nonBinding.add(m.getName());
                    }
                });
        if (nonBinding.isEmpty()) {
            return StringUtils.EMPTY_STRING_ARRAY;
        } else {
            return nonBinding.toArray(String[]::new);
        }
    }

    /**
     * @return The annotation name.
     */
    public String getName() {
        return classConfig.getElement().getName();
    }

    /**
     * @return The class element.
     */
    public ClassElement getElement() {
        return classConfig.getElement();
    }
}
