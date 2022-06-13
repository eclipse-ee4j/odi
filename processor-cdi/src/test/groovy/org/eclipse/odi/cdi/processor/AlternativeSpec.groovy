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
import io.micronaut.context.annotation.Requires

class AlternativeSpec extends AbstractTypeElementSpec {

    void "test @Alternative on type"() {
        given:
        def definition = buildBeanDefinition('alttest.Test', '''
package alttest;

import jakarta.enterprise.inject.Alternative;

@Alternative
class Test {

}
''')
        expect:
        definition != null
        definition.stringValue(Requires, "condition").isPresent()
    }
}
