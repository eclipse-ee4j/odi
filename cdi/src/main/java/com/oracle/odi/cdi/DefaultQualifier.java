package com.oracle.odi.cdi;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Named;

import java.util.List;
import java.util.stream.Stream;

public class DefaultQualifier<T> implements Qualifier<T> {

    private static final io.micronaut.context.Qualifier DEFAULT_QUALIFIER = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Default.class);

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> {
            if (!beanType.isAssignableFrom(candidate.getBeanType())) {
                return false;
            }
            if (candidate instanceof BeanDefinition) {
                Qualifier<?> declaredQualifier = getDeclaredQualifier(candidate.getAnnotationMetadata());
                return declaredQualifier == null
                        || declaredQualifier.contains(DEFAULT_QUALIFIER)
                        || declaredQualifier instanceof Named; // CDI applies @Default also on @Named
            }
            return false;
        });
    }

    Qualifier<T> getDeclaredQualifier(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getDeclaredMetadata();
        final List<String> annotations = annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (CollectionUtils.isNotEmpty(annotations)) {
            if (annotations.size() == 1) {
                final String annotation = annotations.iterator().next();
                if (annotation.equals(Qualifier.PRIMARY)) {
                    // primary is the same as null
                    return null;
                }
                return Qualifiers.byAnnotation(annotationMetadata, annotation);
            } else {
                Qualifier<T>[] qualifiers = new Qualifier[annotations.size()];
                int i = 0;
                for (String name : annotations) {
                    qualifiers[i++] = Qualifiers.byAnnotation(annotationMetadata, name);
                }
                return Qualifiers.byQualifiers(qualifiers);
            }
        }
        return null;
    }

}
