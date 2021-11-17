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

package com.oracle.odi.cdi.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptedProxy
import io.micronaut.runtime.context.scope.ScopedProxy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped

import javax.inject.Scope

class NormalScopeSpec extends AbstractTypeElementSpec {

    void 'test compile class with application scope'() {
        given:
        def definition = buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
class Test {

}

''')
        expect:
        definition != null
        definition.getAnnotationNamesByStereotype(Scope).contains(ApplicationScoped.name)
        definition.hasStereotype(ScopedProxy)
    }

    void 'test compile class with request scope'() {
        given:
        def definition = buildBeanDefinition('appscope.Test', '''
package appscope;

import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Named;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Stereotype
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ApplicationScoped
@Named
@interface FishStereotype {
    
}
@FishStereotype
@RequestScoped
class Test {

}

''')
        expect:
        definition != null
        definition.getAnnotationNamesByStereotype(Scope).contains(RequestScoped.name)
        definition.getAnnotationNamesByStereotype(Scope).contains(ApplicationScoped.name)
        definition.hasStereotype(ScopedProxy)
        definition.hasStereotype(ApplicationScoped)
        definition.hasStereotype(RequestScoped)
    }

    void "test built bean is a scoped proxy"() {
        given:
        def context = buildContext('''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
class Test {

}

''')
        expect:
        getBean(context, 'appscope.Test') instanceof InterceptedProxy
    }

    void 'test fail compilation with normal scope final class'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
final class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Class must be made non-final to support proxying')
    }
}
