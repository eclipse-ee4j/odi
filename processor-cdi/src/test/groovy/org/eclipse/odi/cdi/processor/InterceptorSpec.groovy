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
}
