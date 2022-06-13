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

package org.eclipse.odi.cdi.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Unroll

class NamedValidationSpec extends AbstractTypeElementSpec {


    void 'test fail compilation for invalid name'() {
        when:
        buildBeanDefinition('named.Test', '''
package named;

@jakarta.inject.Named("(£$&")
final class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('@Named annotation specifies an invalid name')
    }

    @Unroll
    void 'test valid name #name'() {
        expect:
        buildBeanDefinition('named.Test', """
package named;

@jakarta.inject.Named("$name")
final class Test {

}

""")
        where:
        name << ["com.acme.settings", "orderManager"]
    }
}
