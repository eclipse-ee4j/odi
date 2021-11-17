package com.oracle.odi.cdi.processor.extensions.spi

import com.oracle.odi.cdi.processor.extensions.ActiveVisitorContext
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.VisitorContext
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder
import jakarta.enterprise.lang.model.AnnotationInfo
import jakarta.enterprise.lang.model.AnnotationMember
import jakarta.enterprise.lang.model.types.ClassType
import spock.lang.Specification
import spock.lang.Unroll

class AnnotationBuilderSpec extends Specification {

    @Unroll
    void "test build annotation info with value for #kind"() {
        given:
        AnnotationInfo info = AnnotationBuilder.of(TestAnn)
                .value(value)
                .build()

        expect:
        info.value()."as$kind"() == value
        info.value().kind() == AnnotationMember.Kind."${kind.toUpperCase()}"

        where:
        value       | kind
        10          | "Int"
        10L         | "Long"
        10 as short | "Short"
        10 as byte  | "Byte"
        1.1d        | "Double"
        1.1f        | "Float"
        "foo"       | "String"
    }

    @Unroll
    void "test build annotation info with value for array #kind"() {
        given:
        AnnotationInfo info = AnnotationBuilder.of(TestAnn)
                .value(value)
                .build()

        expect:
        info.value().asArray().size() == 1
        info.value().asArray().first().kind() == AnnotationMember.Kind."${kind.toUpperCase()}"
        info.value().kind() == AnnotationMember.Kind.ARRAY

        where:
        value               | kind
        [10] as int[]       | "Int"
        ["foo"] as String[] | "String"
    }

    void "test build annotation info with value for nested annotation"() {
        given:
        AnnotationInfo info = AnnotationBuilder.of(TestAnn)
                .value(AnnotationBuilder.of(TestAnn).build())
                .build()

        expect:
        info.value().asNestedAnnotation().name() == TestAnn.name
        info.value().kind() == AnnotationMember.Kind.NESTED_ANNOTATION
    }

    void "test build annotation info with value for type"() {
        given:
        AnnotationInfo info = AnnotationBuilder.of(TestAnn)
                .value(String)
                .build()
        def visitorContext = Mock(VisitorContext)
        visitorContext.getClassElement(_) >> { args -> Optional.of(ClassElement.of(args[0])) }
        ActiveVisitorContext.setVisitorContext(visitorContext)

        expect:
        info.value().asType() instanceof ClassType
        info.value().kind() == AnnotationMember.Kind.CLASS
        info.declaration().name() == TestAnn.name

        cleanup:
        ActiveVisitorContext.setVisitorContext(null)

    }

    @Unroll
    void "test build annotation info with member for #kind"() {
        given:
        AnnotationInfo info = AnnotationBuilder.of(TestAnn)
                .member("foo", value)
                .build()

        expect:
        info.member("foo")."as$kind"() == value

        where:
        value       | kind
        10          | "Int"
        10L         | "Long"
        10 as short | "Short"
        10 as byte  | "Byte"
        1.1d        | "Double"
        1.1f        | "Float"
        "foo"       | "String"
    }
}

@interface TestAnn {}
