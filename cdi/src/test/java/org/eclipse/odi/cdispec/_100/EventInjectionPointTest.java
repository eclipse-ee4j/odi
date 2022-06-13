package org.eclipse.odi.cdispec._100;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Set;

@Disabled("Pending feature in core: https://github.com/micronaut-projects/micronaut-core/pull/6058")
@OdiTest
public class EventInjectionPointTest {

    @Test
    void testMethodAndFieldInjectionPoint(Event<Object> objectEvent, EventService eventService) {
        objectEvent.fire(new StartEvent());
        InjectionPoint methodEventInjectionPoint = eventService.getMethodEventInjectionPoint();
        InjectionPoint fieldEventInjectionPoint = eventService.getFieldEventInjectionPoint();
        InjectionPoint constructorEventInjectionPoint = eventService.getConstructorEventInjectionPoint();

        Assertions.assertNotNull(methodEventInjectionPoint);
        Assertions.assertNotNull(fieldEventInjectionPoint);
        Assertions.assertNotNull(constructorEventInjectionPoint);

        Set<Annotation> methodEventPublisherQualifiers = methodEventInjectionPoint.getQualifiers();
        Assertions.assertEquals(2, methodEventPublisherQualifiers.size());
        Assertions.assertTrue(methodEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Abc));
        Assertions.assertTrue(methodEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Bar));

        Annotated methodEventPublisherAnnotated = methodEventInjectionPoint.getAnnotated();
        Assertions.assertEquals(2, methodEventPublisherAnnotated.getAnnotations().size());
        Assertions.assertNotNull(methodEventPublisherAnnotated.getAnnotation(Abc.class));
        Assertions.assertNotNull(methodEventPublisherAnnotated.getAnnotation(Bar.class));

        Set<Annotation> fieldEventPublisherQualifiers = fieldEventInjectionPoint.getQualifiers();
        Assertions.assertEquals(2, fieldEventPublisherQualifiers.size());
        Assertions.assertTrue(fieldEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Xyz));
        Assertions.assertTrue(fieldEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Foo));

        Annotated fieldEventPublisherAnnotated = fieldEventInjectionPoint.getAnnotated();
        Set<Annotation> annotations = fieldEventPublisherAnnotated.getAnnotations();
        Assertions.assertEquals(3, annotations.size());
//        Assertions.assertNotNull(fieldEventPublisherAnnotated.getAnnotation(javax.inject.Inject.class));
        Assertions.assertNotNull(fieldEventPublisherAnnotated.getAnnotation(Xyz.class));
        Assertions.assertNotNull(fieldEventPublisherAnnotated.getAnnotation(Foo.class));

        Set<Annotation> constructorEventPublisherQualifiers = constructorEventInjectionPoint.getQualifiers();
        Assertions.assertEquals(2, constructorEventPublisherQualifiers.size());
        Assertions.assertTrue(constructorEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Foo));
        Assertions.assertTrue(constructorEventPublisherQualifiers.stream().anyMatch(a -> a instanceof Bar));

        Annotated constructorEventPublisherAnnotated = constructorEventInjectionPoint.getAnnotated();
        Assertions.assertEquals(2, constructorEventPublisherAnnotated.getAnnotations().size());
        Assertions.assertNotNull(constructorEventPublisherAnnotated.getAnnotation(Foo.class));
        Assertions.assertNotNull(constructorEventPublisherAnnotated.getAnnotation(Bar.class));

        Assertions.assertEquals(methodEventInjectionPoint.getType(), MethodEvent.class);
        Assertions.assertEquals(fieldEventInjectionPoint.getType(), FieldEvent.class);
        Assertions.assertEquals(constructorEventInjectionPoint.getType(), ConstructorEvent.class);

        Assertions.assertEquals(fieldEventInjectionPoint.getBean().getBeanClass(), EventService.class);
        Assertions.assertEquals(constructorEventInjectionPoint.getBean().getBeanClass(), EventService.class);
        Assertions.assertEquals(methodEventInjectionPoint.getBean().getBeanClass(), EventService.class);
    }

}

@Dependent
class EventService {

    @Xyz
    @Foo
    @Inject
    private Event<FieldEvent> fieldEventPublisher;

    private final Event<ConstructorEvent> constructorEventPublisher;

    private InjectionPoint methodEventInjectionPoint;
    private InjectionPoint fieldEventInjectionPoint;
    private InjectionPoint constructorEventInjectionPoint;

    EventService(@Foo @Bar Event<ConstructorEvent> constructorEventPublisher) {
        this.constructorEventPublisher = constructorEventPublisher;
    }

    void fireEventsOnStart(@Observes StartEvent event, @Abc @Bar Event<MethodEvent> methodEventPublisher) {
        methodEventPublisher.fire(new MethodEvent());
        fieldEventPublisher.fire(new FieldEvent());
        constructorEventPublisher.fire(new ConstructorEvent());
    }

    void catchMethodEvent(@Observes MethodEvent methodEvent, EventMetadata eventMetadata) {
        methodEventInjectionPoint = eventMetadata.getInjectionPoint();
    }

    void catchFieldEvent(@Observes FieldEvent fieldEvent, EventMetadata eventMetadata) {
        fieldEventInjectionPoint = eventMetadata.getInjectionPoint();
    }

    void catchConstructorEvent(@Observes ConstructorEvent constructorEvent, EventMetadata eventMetadata) {
        constructorEventInjectionPoint = eventMetadata.getInjectionPoint();
    }

    public InjectionPoint getMethodEventInjectionPoint() {
        return methodEventInjectionPoint;
    }

    public InjectionPoint getFieldEventInjectionPoint() {
        return fieldEventInjectionPoint;
    }

    public InjectionPoint getConstructorEventInjectionPoint() {
        return constructorEventInjectionPoint;
    }
}

class StartEvent {
}

class MethodEvent {
}

class FieldEvent {
}

class ConstructorEvent {
}

