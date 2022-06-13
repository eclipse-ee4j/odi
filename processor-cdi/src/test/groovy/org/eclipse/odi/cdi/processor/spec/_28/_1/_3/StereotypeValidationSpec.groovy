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

package org.eclipse.odi.cdi.processor.spec._28._1._3


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import org.eclipse.odi.cdi.processor.CdiUtil
import spock.lang.Narrative

import javax.inject.Named

@Narrative("CDI Spec - 2.8.1.3. Declaring a @Named stereotype")
class StereotypeValidationSpec extends AbstractTypeElementSpec {

    void 'test that compilation succeeds with @Named and no value'() {
        when:
        def definition = buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.*;

@Action
class Test {

}

@Stereotype
@Named
@Retention(RUNTIME)
@interface Action {}
''')
        then:
        definition != null
        definition.stringValue(Named).get() == 'test'

    }

    void 'test that compilation succeeds with @Named and no value but with @Named on the class'() {
        when:
        def definition = buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.*;

@Action
@Named("test")
class Test {

}

@Stereotype
@Named
@Retention(RUNTIME)
@interface Action {}
''')
        then:
        definition != null
        definition.stringValue(Named).get() == 'test'

    }

    void 'test that compilation fails with @Named and a value'() {
        when:
        buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.*;

@Action
class Test {

}

@Stereotype
@Named("bad")
@Retention(RUNTIME)
@interface Action {}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Stereotypes [@Action] cannot define a @Named qualifier with a value. See ${CdiUtil.SPEC_LOCATION}#named_stereotype")

    }

    void "test that any other qualifier types result in an error"() {
        when:
        buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.*;

@Action
class Test {

}

@Stereotype
@Retention(RUNTIME)
@MyQualifier
@interface Action {}

@Retention(RUNTIME)
@Qualifier
@interface MyQualifier {}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Inherited stereotypes [@Action] cannot declare qualifiers: @MyQualifier. See ${CdiUtil.SPEC_LOCATION}#named_stereotype")
    }

    void 'test that compilation fails if @Typed is declared on a stereotype'() {
        when:
        buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.*;

@Action
class Test implements Marker {

}

@Stereotype
@Retention(RUNTIME)
@jakarta.enterprise.inject.Typed(Marker.class)
@interface Action {}

interface Marker {}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Inherited stereotypes [@Action] cannot declare @Typed. See ${CdiUtil.SPEC_LOCATION}#named_stereotype")
    }
}
