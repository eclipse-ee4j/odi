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
package com.oracle.odi.cdi.processor.extensions.javax;

import com.oracle.odi.cdi.processor.transformers.InterceptorBindingTransformer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.Interceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Remapper that adds support for javax.interceptor.
 */
public final class JavaxInterceptorRemapper implements AnnotationRemapper {

    @Override
    public String getPackageName() {
        return "javax.interceptor";
    }

    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        final String annotationName = annotation.getAnnotationName();
        final String simpleName = NameUtils.getSimpleName(annotationName);
        if (simpleName.startsWith("Around")) {
            return Arrays.asList(
                AnnotationValue.builder(Executable.class).build(),
                AnnotationValue.builder(
                        annotationName.replace("javax.", "jakarta.")
                ).members(annotation.getValues()).build()
            );
        } else if (annotationName.equals("javax.interceptor.Interceptor")) {
            return Arrays.asList(
                    AnnotationValue.builder(Bean.class).build(),
                    AnnotationValue.builder(Interceptor.class).build()
            );
        } else if (annotationName.equals("javax.interceptor.InterceptorBinding")) {
            return InterceptorBindingTransformer.INTERCEPTOR_BINDING_VALUES;
        } else {
            return Collections.singletonList(
                    AnnotationValue.builder(
                            annotationName.replace("javax.", "jakarta.")
                    ).members(annotation.getValues()).build()
            );
        }
    }
}
