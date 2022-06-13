package org.eclipse.odi.cdi.annotation.type


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.visitor.VisitorContext
import jakarta.enterprise.inject.build.compatible.spi.Types
import jakarta.enterprise.lang.model.declarations.ClassInfo
import jakarta.enterprise.lang.model.declarations.DeclarationInfo
import jakarta.enterprise.lang.model.declarations.MethodInfo
import jakarta.enterprise.lang.model.types.ClassType
import jakarta.enterprise.lang.model.types.PrimitiveType
import jakarta.enterprise.lang.model.types.Type
import jakarta.enterprise.lang.model.types.VoidType
import jakarta.inject.Singleton
import org.eclipse.odi.cdi.processor.extensions.TypeFactory
import org.eclipse.odi.cdi.processor.extensions.VoidTypeImpl

import java.lang.reflect.Modifier

class TypeFactorySpec extends AbstractTypeElementSpec {

    void 'test simple class info'() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

@jakarta.inject.Singleton
final class Test {
    final String foo = "one";
    
    Test() {}
    
    Test(String foo) {}
    
    void test1(int foo) {}
    
    void test2() {}
    
}
''')

        when:
        def mock = Mock(VisitorContext)
        def types = Mock(Types)
        types.ofVoid() >> new VoidTypeImpl(PrimitiveElement.VOID, types, mock)
        mock.getClassElement('test.Test') >> Optional.of(classElement)
        Type type = TypeFactory.createType(classElement, types, mock)

        then:
        type instanceof ClassType
        type.declaration() instanceof ClassInfo

        when:
        ClassInfo classInfo = type.declaration()

        then:
        Modifier.isFinal(classInfo.modifiers())
        !classInfo.isAbstract()
        classInfo.isFinal()
        !classInfo.isAnnotation()
        !classInfo.isEnum()
        !classInfo.isInterface()
        classInfo.isPlainClass()
        classInfo.fields().size() == 1
        classInfo.fields().first().name() == 'foo'
        classInfo.constructors().size() == 2
        classInfo.methods().size() == 2
        classInfo.hasAnnotation(Singleton)

        when:
        MethodInfo mi = classInfo.methods().find { MethodInfo i -> i.name() == 'test1' }

        then:
        mi.returnType() instanceof VoidType
        mi.kind() == DeclarationInfo.Kind.METHOD
        mi.parameters()[0].type() instanceof PrimitiveType
    }
}
