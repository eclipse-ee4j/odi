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
package com.oracle.odi.cdi.condition;

import java.util.StringTokenizer;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;

/**
 * A custom condition that allows controlling selected CDI alternatives.
 */
@Introspected
@Internal
public final class SelectedAlternativeCondition implements Condition {
    /**
     * Selected alternatives environment variable.
     */
    public static final String PROPERTY = "odi.selected-alternatives";

    @Override
    public boolean matches(ConditionContext context) {
        final BeanContext beanContext = context.getBeanContext();
        final AnnotationMetadataProvider component = context.getComponent();
        if (component instanceof BeanDefinition) {
            final Class<?> beanType = ((BeanDefinition<?>) component).getBeanType();
            if (beanContext instanceof ApplicationContext) {
                final Environment env = ((ApplicationContext) beanContext).getEnvironment();
                final String selectedAlternatives =
                        env.getProperty(PROPERTY, ConversionContext.STRING)
                                .orElse(null);
                if (StringUtils.isNotEmpty(selectedAlternatives)) {

                    final StringTokenizer tokenizer = new StringTokenizer(selectedAlternatives, ",");
                    AntPathMatcher antPathMatcher = new AntPathMatcher();
                    while (tokenizer.hasMoreTokens()) {
                        final String token = tokenizer.nextToken();
                        if (antPathMatcher.matches(token, beanType.getName())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } else {
            return false;
        }
    }
}
