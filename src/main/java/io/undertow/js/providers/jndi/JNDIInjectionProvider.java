package io.undertow.js.providers.jndi;

import io.undertow.js.InjectionProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;

public class JNDIInjectionProvider implements InjectionProvider {

    @Override
    public Object getObject(String name) {
        try {
            return new InitialContext().lookup(name);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPrefix() {
        return "jndi";
    }
}