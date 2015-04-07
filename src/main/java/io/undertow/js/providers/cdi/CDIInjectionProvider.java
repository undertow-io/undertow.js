package io.undertow.js.providers.cdi;

import io.undertow.js.InjectionProvider;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class CDIInjectionProvider implements InjectionProvider {

        @Override
        public Object getObject(String name) {
            try {
                BeanManager beanManager = (BeanManager) new InitialContext().lookup("java:comp/BeanManager");
                Bean<?> bean = beanManager.resolve(beanManager.getBeans(name));

                Class<?> t = Object.class;
                Type realType = t;
                for (Type type : bean.getTypes()) {
                    Class c;
                    if(type instanceof Class) {
                        c = (Class) type;
                    } else if(type instanceof ParameterizedType) {
                        c = (Class)((ParameterizedType) type).getRawType();
                    } else {
                        continue;
                    }
                    if(t.isAssignableFrom(c)) {
                        t = c;
                        realType = type;
                    }
                }
                return beanManager.getReference(bean, realType, beanManager.createCreationalContext(bean));
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getPrefix() {
            return "cdi";
        }
    }