package com.nurkiewicz.progress;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2018 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationListener;

import com.ethlo.geodata.DataLoadedEvent;

import rx.Observable;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

public class ProgressBeanPostProcessor implements BeanPostProcessor, ApplicationListener<DataLoadedEvent>
{
    private static final Logger log = LoggerFactory.getLogger(ProgressBeanPostProcessor.class);

    private static final Subject<String, String> beans = ReplaySubject.create();

    public Object postProcessBeforeInitialization(Object bean, String beanName)
    {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
    {
        beans.onNext(beanName);
        return wrapIfServletContainerFactory(bean);
    }

    private Object wrapIfServletContainerFactory(Object bean)
    {
        if (bean instanceof EmbeddedServletContainerFactory)
        {
            return wrap((EmbeddedServletContainerFactory) bean);
        }
        else
        {
            return bean;
        }
    }

    private EmbeddedServletContainerFactory wrap(EmbeddedServletContainerFactory factory)
    {
        if (factory instanceof TomcatEmbeddedServletContainerFactory)
        {
            ((TomcatEmbeddedServletContainerFactory) factory).addContextValves(new ProgressValve());
        }
        return initializers -> {
            final EmbeddedServletContainer container = factory.getEmbeddedServletContainer(initializers);
            log.debug("Eagerly starting {}", container);
            container.start();
            return container;
        };
    }

    @Override
    public void onApplicationEvent(DataLoadedEvent event)
    {
        beans.onNext(event.getName() + ":" + event.getProgress());
        
        if ("complete".equals(event.getName()))
        {
            beans.onCompleted();
        }

    }

    static Observable<String> observe()
    {
        return beans;
    }
}
