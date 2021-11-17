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

package com.oracle.odi.cdi.processor.spec._37

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Disposes
import spock.lang.Unroll

class InitializerMethodSpec extends AbstractTypeElementSpec {

    @Unroll
    void "test that initializer methods annotated with @Inject can't have invalid annotation #annotation"() {
        when:
        buildBeanDefinition('ctortest.Test', """
package ctortest;

import jakarta.inject.*;

@Singleton
class Test {
    @Inject
    void init(@$annotation.name String one) {}
    
}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Methods annotated with @Inject cannot define parameters annotated with @$annotation.simpleName")

        where:
        annotation << [Disposes, Observes, ObservesAsync]
    }
}
