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

package org.eclipse.odi.cdi.processor.spec._32._1

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Unroll

class ProducesMethodValidationSpec extends AbstractTypeElementSpec {

    @Unroll
    void "test fail compilation for invalid annotation #annotation"() {
        when:
        buildBeanDefinition('test.Test', """
package test;

import jakarta.inject.Singleton;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.event.*;

@Singleton
class Shop {
    @Produces
    String getBean(@$annotation String other) {
        return null;
    }
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Produces methods cannot contain parameters annotated with @$annotation")

        where:
        annotation << ['Disposes', 'Observes', 'ObservesAsync']
    }

    void "test fail compilation for inject and produces annotation #annotation"() {
        when:
        buildBeanDefinition('test.Test', """
package test;

import jakarta.inject.*;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.event.*;

@Singleton
class Shop {
    @Produces
    @Inject
    String getBean() {
        return null;
    }
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Produces methods cannot be annotated with @Inject")
    }

    void "test fail compilation for produces annotation on method in interceptor #annotation"() {
        when:
        buildBeanDefinition('test.Test', """
package test;

import jakarta.inject.*;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.event.*;

@Singleton
@jakarta.interceptor.Interceptor
class Shop {
    @Produces
    String getBean() {
        return null;
    }
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Interceptors cannot define methods annotated with @Produces")
    }
}
