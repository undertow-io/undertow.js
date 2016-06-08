/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.js.providers.jndi;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import io.undertow.js.InjectionContext;
import io.undertow.js.InjectionProvider;

/**
*
* @author Stuart Douglas
*
*/
public class JNDIInjectionProvider implements InjectionProvider {

    @Override
    public Object getObject(InjectionContext injectionContext) {
        try {
            return new InitialContext().lookup(injectionContext.getName());
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPrefix() {
        return "jndi";
    }
}
