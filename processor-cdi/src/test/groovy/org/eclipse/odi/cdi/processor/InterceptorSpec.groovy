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

import org.eclipse.odi.cdi.intercept.JakartaInterceptorAdapter
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.aop.Around
import io.micronaut.core.type.Argument
import org.eclipse.odi.cdi.annotation.DisposerMethod

class InterceptorSpec extends AbstractTypeElementSpec {

    void 'test around invoke interceptor'() {
        given:
        def context = buildContext('''
package intertest;

import jakarta.interceptor.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Singleton
class Test {
    @Monitored
    public void test() {}
}


@Monitored @Interceptor
class MonitoringInterceptor {
    public boolean invoked = false;
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx)
        throws Exception { 
        invoked = true;
        return ctx.proceed();         
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')
        when:
        def bean = getBean(context, 'intertest.Test')
        def interceptor = getBean(context, 'intertest.MonitoringInterceptor')

        then:
        bean instanceof Intercepted
        !(interceptor instanceof Intercepted)
        bean.@$interceptors[0][0] instanceof JakartaInterceptorAdapter
        bean.@$interceptors[0][0].aroundInvoke.name == 'monitorInvocation'
    }

    void 'test around invoke interceptor binding with member'() {
        given:
        def context = buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Dependent
class Test {
    @Counted(Mode.INCREASE)
    public int count() {
        return 10;
    }
}

@Counted(Mode.INCREASE)
@Interceptor
class CountInterceptor {
    @AroundInvoke
    public Object around(InvocationContext ctx) throws Exception {
        return ((Integer) ctx.proceed()) + 10;
    }
}

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Counted {
    Mode value();
}

enum Mode {
    INCREASE,
    DECREASE
}
''')

        when:
        def bean = getBean(context, 'intertest.Test')

        then:
        bean instanceof Intercepted
        bean.count() == 20
    }

    void 'test fail compilation for intercepted bean without bean constructor'() {
        when:
        buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Monitored
@Dependent
class Test {
    private Test() {
    }

    Test(String name) {
    }

    public void test() {
    }
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Intercepted bean classes must declare a non-private no-arguments constructor or an @Inject constructor')
    }

    void 'test intercepted bean with inject constructor compiles'() {
        expect:
        buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Monitored
@Dependent
class Test {
    @Inject
    Test(BeanManager beanManager) {
    }

    public void test() {
    }
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')
    }

    void 'test fail compilation for non-dependent interceptor'() {
        when:
        buildContext('''
package intertest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@RequestScoped
@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Interceptors must have @Dependent scope')
    }

    void 'test fail compilation for intercepted final class'() {
        when:
        buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Monitored
@Dependent
final class Test {
    public void test() {
    }
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Intercepted bean classes must not be final')
    }

    void 'test fail compilation for intercepted final business method'() {
        when:
        buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Monitored
@Dependent
class Test {
    public final void test() {
    }
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Intercepted bean methods must not be non-private final methods')
    }

    void 'test final method not bound to interceptor compiles'() {
        expect:
        buildContext('''
package intertest;

import jakarta.enterprise.context.Dependent;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@Dependent
class Test {
    @Monitored
    public void test() {
    }

    public final void helper() {
    }
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')
    }

    void 'test disposer method on normal scoped intercepted bean is available on proxy definition'() {
        given:
        def context = buildContext('''
package intertest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

@RequestScoped
@Monitored
class WheatProducer {
    @Produces
    public Wheat createWheat() {
        return new Wheat();
    }

    public void destroyWheat(@Disposes Wheat wheat) {
    }
}

class Wheat {
}

@Monitored
@Interceptor
class MonitoringInterceptor {
    @AroundInvoke
    public Object monitorInvocation(InvocationContext ctx) throws Exception {
        return ctx.proceed();
    }
}

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Monitored {}
''')
        def producerType = context.classLoader.loadClass('intertest.WheatProducer')
        def wheatType = context.classLoader.loadClass('intertest.Wheat')

        when:
        def proxyDefinition = context.findProxyBeanDefinition(Argument.of(producerType), null)
        def targetDefinition = context.findProxyTargetBeanDefinition(Argument.of(producerType), null)
        def producedDefinition = context.getBeanDefinitions(Argument.of(wheatType)).find {
            it.declaringType.orElse(null) == producerType
        }

        then:
        proxyDefinition.present
        targetDefinition.present
        producedDefinition != null
        producedDefinition.hasAnnotation(DisposerMethod)
        proxyDefinition.get().findMethod('destroyWheat', wheatType).present
        targetDefinition.get().findMethod('destroyWheat', wheatType).present
        targetDefinition.get().findMethod('destroyWheat', wheatType).get().hasAnnotation(Around)
    }
}
