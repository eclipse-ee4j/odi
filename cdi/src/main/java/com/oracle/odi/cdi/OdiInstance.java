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
package com.oracle.odi.cdi;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.stream.Stream;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.util.TypeLiteral;

final class OdiInstance<T> implements Instance<T> {
    private final BeanContext beanContext;
    private final Argument<T> beanType;
    private final Annotation[] qualifiers;
    private final BeanResolutionContext resolutionContext;
    private Qualifier<T> qualifier;

    OdiInstance(@Nullable BeanResolutionContext resolutionContext,
                BeanContext beanContext,
                Argument<T> beanType,
                Annotation... qualifiers) {
        this.resolutionContext = resolutionContext;
        this.beanContext = beanContext;
        this.beanType = beanType;
        this.qualifiers = qualifiers;
    }

    OdiInstance(@Nullable BeanResolutionContext resolutionContext,
                       BeanContext context,
                       Argument<T> argument,
                       Qualifier<T> qualifier) {
        this(resolutionContext, context, argument);
        this.qualifier = qualifier;
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        return new OdiInstance<>(resolutionContext, beanContext, beanType, qualifiers);
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new OdiInstance<U>(resolutionContext, beanContext, Argument.of(subtype), qualifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return new OdiInstance<>(resolutionContext, beanContext, (Argument<U>) Argument.of(subtype.getType()), qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        return beanContext.findBeanDefinition(beanType, resolveQualifier()).isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
        try {
            beanContext.getBeanDefinition(beanType, resolveQualifier());
            return false;
        } catch (NonUniqueBeanException e) {
            return true;
        } catch (NoSuchBeanException e) {
            return false;
        }
    }

    @Override
    public void destroy(T instance) {
        beanContext.destroyBean(instance);
    }

    @Override
    public T get() {
        try {
            return ((DefaultBeanContext) beanContext).getBean(resolutionContext, beanType, resolveQualifier());
        } catch (NonUniqueBeanException e) {
            throw new AmbiguousResolutionException(e.getMessage(), e);
        } catch (NoSuchBeanException e) {
            throw new UnsatisfiedResolutionException(e.getMessage(), e);
        } catch (Throwable e) {
            throw new CreationException(e.getMessage(), e);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    @Override
    public Stream<T> stream() {
        Qualifier<T> qualifier = resolveQualifier();
        return ((DefaultBeanContext) beanContext).streamOfType(resolutionContext, beanType, qualifier);
    }

    private Qualifier<T> resolveQualifier() {
        if (qualifier != null) {
            return qualifier;
        } else {
            return resolveQualifier(qualifiers);
        }
    }

    static <T1> Qualifier<T1> resolveQualifier(Annotation[] annotations) {
        if (ArrayUtils.isNotEmpty(annotations)) {
            return AnnotationUtils.qualifierFromQualifierAnnotations(annotations);
        }
        return null;
    }
}
