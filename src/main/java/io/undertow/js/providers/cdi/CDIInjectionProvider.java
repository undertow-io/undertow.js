/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.js.providers.cdi;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import io.undertow.js.InjectionContext;
import io.undertow.js.InjectionProvider;
import io.undertow.js.UndertowScriptLogger;

/**
 *
 * @author Stuart Douglas
 * @author Martin Kouba
 *
 */
public final class CDIInjectionProvider implements InjectionProvider {

    private volatile BeanManager beanManager;

    @Override
    public Object getObject(InjectionContext injectionContext) {
        if (beanManager == null) {
            lookupBeanManager();
        }
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(injectionContext.getName()));
        if (bean == null) {
            throw UndertowScriptLogger.ROOT_LOGGER.couldNotFindBean(injectionContext.getName());
        }
        return getReference(bean, injectionContext);
    }

    @Override
    public String getPrefix() {
        return "cdi";
    }

    private <T> Object getReference(final Bean<T> bean, InjectionContext injectionContext) {

        final CreationalContext<T> creationalContext = beanManager.createCreationalContext(bean);

        if (Dependent.class.equals(bean.getScope())) {
            final T reference = bean.create(creationalContext);
            injectionContext.whenHandlerDiscarded(() -> bean.destroy(reference, creationalContext));
            return reference;
        } else {
            return beanManager.getReference(bean, Object.class, creationalContext);
        }
    }

    private synchronized void lookupBeanManager() {
        if (beanManager == null) {
            try {
                beanManager = (BeanManager) new InitialContext().lookup("java:comp/BeanManager");
            } catch (NamingException ignored) {
            }
            if (beanManager == null) {
                beanManager = CDI.current().getBeanManager();
            }
            if (beanManager == null) {
                throw UndertowScriptLogger.ROOT_LOGGER.unableToLookupBeanManager();
            }
        }
    }
}