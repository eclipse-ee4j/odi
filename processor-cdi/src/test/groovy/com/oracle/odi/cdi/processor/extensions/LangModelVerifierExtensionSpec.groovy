package com.oracle.odi.cdi.processor.extensions

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.provider.JavaxProviderBeanDefinition
import io.micronaut.inject.visitor.TypeElementVisitor
import org.jboss.cdi.lang.model.tck.LangModelVerifier

class LangModelVerifierExtensionSpec extends AbstractTypeElementSpec {

    void "test lang model extension"() {
        given:
            LangModelVerifier.EXCLUDE_TYPE_ANNOTATION_CHECKS = true
            LangModelVerifierExtension.enable = true
            def context = buildContext('''
package testft;

@jakarta.inject.Singleton
class MyService {
    public String hello() {
        return "ok";
    }
    
}
''')
        when:
            context.getBeanDefinition(context.classLoader.loadClass('testft.MyService'))
            def exception = LangModelVerifierExtension.exception
        then:
            if (exception) {
                exception.printStackTrace()
            }
            !exception
            LangModelVerifierExtension.passed
        cleanup:
            LangModelVerifierExtension.enable = false
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
