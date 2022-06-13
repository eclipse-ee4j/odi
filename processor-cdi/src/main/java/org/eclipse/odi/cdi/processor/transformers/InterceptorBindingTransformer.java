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
package org.eclipse.odi.cdi.processor.transformers;

import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.InterceptorBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms {@link jakarta.interceptor.InterceptorBinding} into appropriate Micronaut annotations.
 */
public class InterceptorBindingTransformer implements TypedAnnotationTransformer<InterceptorBinding> {
    /**
     * Possible annotation binding values.
     */
    public static final List<AnnotationValue<?>> INTERCEPTOR_BINDING_VALUES =
            List.of(
                    AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                            .member("kind", InterceptorKind.AROUND)
                            .member("bindMembers", true)
                            .build(),
                    AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                            .member("kind", InterceptorKind.AROUND_CONSTRUCT)
                            .member("bindMembers", true)
                            .build(),
                    AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                            .member("kind", InterceptorKind.PRE_DESTROY)
                            .member("bindMembers", true)
                            .build(),
                    AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                            .member("kind", InterceptorKind.POST_CONSTRUCT)
                            .member("bindMembers", true)
                            .build()
            );

    @Override
    public Class<InterceptorBinding> annotationType() {
        return InterceptorBinding.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<InterceptorBinding> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<?>> list = new ArrayList<>(INTERCEPTOR_BINDING_VALUES.size() + 1);
        list.add(annotation);
        list.addAll(INTERCEPTOR_BINDING_VALUES);
        return list;
    }
}
