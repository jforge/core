/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.tests.extensions.custombeans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.util.TypeLiteral;

import org.jboss.weld.experimental.BeanBuilder;
import org.jboss.weld.experimental.ExperimentalAfterBeanDiscovery;

/**
 *
 * @author Martin Kouba
 */
public class BuilderExtension implements Extension {

    static final AtomicBoolean DISPOSED = new AtomicBoolean(false);

    public void processAnnotatedType(@Observes ProcessAnnotatedType<? extends VetoedBean> event) {
        event.veto();
    }

    @SuppressWarnings("serial")
    public void afterBeanDiscovery(@Observes ExperimentalAfterBeanDiscovery event, BeanManager beanManager) {

        AnnotatedType<Foo> annotatedType = beanManager.createAnnotatedType(Foo.class);

        // Read from bean attributes, change the name and remove @Model stereotype
        // Note that we have to set the scope manually as it's initialized to @RequestScoped through the bean attributes
        event.addBean().beanClass(Foo.class).read(beanManager.createBeanAttributes(annotatedType)).name("bar")
                .stereotypes(Collections.emptySet()).scope(Dependent.class).produceWith(() -> {
                    Foo foo = new Foo();
                    foo.postConstruct();
                    return foo;
                });

        // Detached builder, read from AT, add qualifier, set id
        BeanBuilder<Foo> builder = event.beanBuilder().read(annotatedType);
        builder.id("BAZinga").addQualifier(Juicy.Literal.INSTANCE);
        event.addBean(builder.build());

        // Read from AT, set the scope
        event.addBean().read(beanManager.createAnnotatedType(Bar.class)).scope(Dependent.class);

        // Test simple produceWith callback
        event.addBean().addType(Integer.class).addQualifier(Random.Literal.INSTANCE)
                .produceWith(() -> new java.util.Random().nextInt(1000)).disposeWith((i) -> DISPOSED.set(true));

        // Test produceWith callback with Instance<Object> param
        event.addBean().addType(Long.class).addQualifier(AnotherRandom.Literal.INSTANCE)
                .produceWith((i) -> i.select(Foo.class, Juicy.Literal.INSTANCE).get().getId() * 2);

        // Test TypeLiteral
        List<String> list = new ArrayList<String>();
        list.add("FOO");
        event.addBean().addType(new TypeLiteral<List<String>>() {
        }).addQualifier(Juicy.Literal.INSTANCE).producing(list);

        // Test transitive type closure
        event.addBean().addTransitiveTypeClosure(Foo.class).addQualifier(Random.Literal.INSTANCE)
                .produceWith(() -> new Foo(-1l));
    }
}
