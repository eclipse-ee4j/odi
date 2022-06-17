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
package org.eclipse.odi.cdi.annotation;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal meta-annotation used to mark classes for self interception. Should not be used directly.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Executable(processOnStartup = true)
@Internal
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding
public @interface SelfInterceptor {

    /**
     * The self class
     * @return self class
     */
    Class<?> self();

}
