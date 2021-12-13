package com.oracle.odi.cdi.processor.extensions

import com.oracle.odi.cdi.intercept.JakartaInterceptorAdapter
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBean
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.annotation.Order
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.provider.JavaxProviderBeanDefinition
import io.micronaut.inject.visitor.TypeElementVisitor
import io.smallrye.faulttolerance.ExistingCircuitBreakerNames
import io.smallrye.faulttolerance.FaultToleranceInterceptor
import jakarta.annotation.Priority

class FaultToleranceExtensionSpec extends AbstractTypeElementSpec {
    def setup() {
        System.setProperty("MP_Fault_Tolerance_Metrics_Enabled", "false")
    }

    def cleanup() {
        System.setProperty("MP_Fault_Tolerance_Metrics_Enabled", "")
    }

    void "test fault tolerance extension"() {
        given:
        def context = buildContext('''
package testft;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.ArgumentInjectionPoint;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import java.io.IOException;
import com.oracle.odi.cdi.processor.extensions.*;
import java.util.Collections;

@Singleton
class MyService {
    public int count = 0;
    @Retry(maxRetries = 10, retryOn = IOException.class) 
    public String hello() throws IOException {
        if (count == 0) {
            count++;
            throw new IOException("bad stuff happened");
        }
        return "good";
    }
    
    @CircuitBreaker
    @CircuitBreakerName("mycb") 
    public String another() throws IOException {
        if (count == 0) {
            count++;
            throw new IOException("bad stuff happened");
        }
        return "good";
    }
}

// dummy bean to mock javax CDI Bean injection
@Factory
class MockBeanFactory {
    @Bean
    @Any
    <T> Instance<Object> getBean() {
        return new MockInstance();
    }
    
    @Bean
    @Any
    <T> javax.enterprise.inject.spi.Bean<T> getBean(ArgumentInjectionPoint<?, ?> injectionPoint) {
        return new MockBeanImpl<>(injectionPoint.asArgument().getType());
    }
    
    
    @Bean
    Parameters config(
            ArgumentInjectionPoint<?, ?> argumentInjectionPoint
    ) {
        return MockParamCreator.create(argumentInjectionPoint);
    }
}
''')
        when:
        BeanDefinition<?> bd = context.getBeanDefinition(context.classLoader.loadClass('testft.MyService'))
        def bean = getBean(context, 'testft.MyService')
        BeanDefinition<FaultToleranceInterceptor> ftdb = context.getBeanDefinition(FaultToleranceInterceptor)
        BeanDefinition<JakartaInterceptorAdapter> adapter = context.getBeanDefinition(JakartaInterceptorAdapter)

        then:
        context.getBean(ExistingCircuitBreakerNames).contains("mycb")
        context.getBean(ExistingCircuitBreakerNames).is(context.getBean(ExistingCircuitBreakerNames))
        ftdb.hasAnnotation(Order)
        ftdb.intValue(Priority).asInt == 333
        ftdb.intValue(Order).asInt == -333
        adapter.hasAnnotation(InterceptorBean)
        context.getBean(FaultToleranceInterceptor)
        bean instanceof Intercepted
        bean.@$interceptors[0].size() == 1

        when:
        def result = bean.hello()

        then:
        bean.count == 1
        result == 'good'

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new BuildTimeExtensionVisitor()]
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.allowEmptyProviders(true)
    }

    @Override
    List<BeanDefinitionReference<?>> getBuiltInBeanReferences() {
        return super.getBuiltInBeanReferences() + new JavaxProviderBeanDefinition()
    }
}
