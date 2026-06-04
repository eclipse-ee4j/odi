/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.odi.cdispec._32._2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;
import org.eclipse.odi.cdi.OdiBean;
import org.eclipse.odi.test.junit5.OdiTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@OdiTest
public class NullProducerScopeTest {

    @Test
    void normalScopedNullProducerThrowsIllegalProductException(BeanContainer beanContainer) {
        Bean<NullProduct> bean = (Bean<NullProduct>) beanContainer.getBeans(NullProduct.class, NullQualifier.Literal.INSTANCE)
                .iterator()
                .next();
        CreationalContext<NullProduct> creationalContext = beanContainer.createCreationalContext(bean);

        assertEquals(ApplicationScoped.class, bean.getScope());
        assertTrue(bean instanceof OdiBean<?>);
        OdiBean<?> odiBean = (OdiBean<?>) bean;
        assertTrue(odiBean.getBeanDefinition().hasAnnotation(Produces.class));
        assertThrows(IllegalProductException.class, () -> bean.create(creationalContext));
    }
}

@Dependent
class NullProductProducer {
    @Produces
    @ApplicationScoped
    @NullQualifier
    NullProduct make() {
        return null;
    }
}

class NullProduct {
}

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface NullQualifier {
    final class Literal extends AnnotationLiteral<NullQualifier> implements NullQualifier {
        static final Literal INSTANCE = new Literal();
    }
}
