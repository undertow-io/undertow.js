/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package io.undertow.js;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import io.undertow.js.templates.TemplateProvider;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.AttachmentKey;
import io.undertow.util.FileUtils;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;

/**
 * Builder class for Undertow Javascipt deployments
 *
 * @author Stuart Douglas
 */
public class UndertowJS {

    private static final AttachmentKey<HttpHandler> NEXT = AttachmentKey.create(HttpHandler.class);

    public static final int HOT_DEPLOYMENT_INTERVAL = 500;
    private final List<ResourceSet> resources;
    private final boolean hotDeployment;
    private final Map<ResourceSet, ResourceChangeListener> listeners = new IdentityHashMap<>();
    private final ClassLoader classLoader;
    private final Map<String, InjectionProvider> injectionProviders;
    private final JavabeanIntrospector javabeanIntrospector = new JavabeanIntrospector();
    private final List<HandlerWrapper> handlerWrappers;
    private final ResourceManager resourceManager;
    private final Map<String, TemplateProvider> templateProviders;

    private ScriptEngine engine;
    private HttpHandler routingHandler;
    private Map<Resource, Date> lastModified;
    private volatile long lastHotDeploymentCheck = -1;
    private volatile Set<String> rejectPaths = Collections.emptySet();

    /**
     *
     * @param resources
     * @param hotDeployment
     * @param classLoader
     * @param injectionProviders
     * @param handlerWrappers
     * @param resourceManager
     * @param templateProviders
     */
    public UndertowJS(List<ResourceSet> resources, boolean hotDeployment, ClassLoader classLoader, Map<String, InjectionProvider> injectionProviders, List<HandlerWrapper> handlerWrappers, ResourceManager resourceManager,  Map<String, TemplateProvider> templateProviders) {
        this.classLoader = classLoader;
        this.injectionProviders = injectionProviders;
        this.handlerWrappers = handlerWrappers;
        this.resources = new ArrayList<>(resources);
        this.hotDeployment = hotDeployment;
        this.resourceManager = resourceManager;
        this.templateProviders = templateProviders;
    }

    public UndertowJS start() throws ScriptException, IOException {
        buildEngine();
        return this;
    }

    public Object evaluate(String code) throws ScriptException {
        return engine.eval(code);
    }

    private synchronized void buildEngine() throws ScriptException, IOException {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("JavaScript");


        RoutingHandler routingHandler = new RoutingHandler(true);
        routingHandler.setFallbackHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getAttachment(NEXT).handleRequest(exchange);
            }
        });
        RoutingHandler wsRoutingHandler = new RoutingHandler(false);
        wsRoutingHandler.setFallbackHandler(routingHandler);

        for (TemplateProvider templateProvider : templateProviders.values()) {
            // TODO properties should be configurable
            templateProvider.init(Collections.emptyMap(), resourceManager);
        }

        UndertowSupport support = new UndertowSupport(routingHandler, classLoader, injectionProviders, javabeanIntrospector, handlerWrappers, resourceManager, wsRoutingHandler, templateProviders);
        engine.put("$undertow_support", support);
        engine.put(ScriptEngine.FILENAME, "undertow-core-scripts.js");
        engine.eval(FileUtils.readFile(UndertowJS.class, "undertow-core-scripts.js"));
        Map<Resource, Date> lm = new HashMap<>();
        final Set<String> rejectPaths = new HashSet<>();
        for (ResourceSet set : resources) {

            for (String resource : set.getResources()) {
                if(resource.startsWith("/")) {
                    rejectPaths.add(resource);
                } else {
                    rejectPaths.add("/" + resource);
                }
                Resource res = set.getResourceManager().getResource(resource);
                if (res == null) {
                    UndertowScriptLogger.ROOT_LOGGER.couldNotReadResource(resource);
                } else {
                    try (InputStream stream = res.getUrl().openStream()) {
                        engine.put(ScriptEngine.FILENAME, res.getUrl().toString());
                        engine.eval(new InputStreamReader(new BufferedInputStream(stream)));
                    }
                    if (hotDeployment) {
                        lm.put(res, res.getLastModified());
                    }
                }
            }
        }
        this.engine = engine;
        this.routingHandler = wsRoutingHandler;
        this.lastModified = lm;
        this.rejectPaths = Collections.unmodifiableSet(rejectPaths);
    }

    public UndertowJS stop() {
        for (Map.Entry<ResourceSet, ResourceChangeListener> entry : listeners.entrySet()) {
            entry.getKey().getResourceManager().removeResourceChangeListener(entry.getValue());
        }
        listeners.clear();
        for (TemplateProvider templateProvider : templateProviders.values()) {
            templateProvider.cleanup();
        }
        engine = null;
        return this;
    }

    public HttpHandler getHandler(final HttpHandler next) {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if (hotDeployment) {
                    long lastHotDeploymentCheck = UndertowJS.this.lastHotDeploymentCheck;
                    if (System.currentTimeMillis() > lastHotDeploymentCheck + HOT_DEPLOYMENT_INTERVAL) {
                        synchronized (UndertowJS.this) {
                            if (UndertowJS.this.lastHotDeploymentCheck == lastHotDeploymentCheck) {
                                for (Map.Entry<Resource, Date> entry : lastModified.entrySet()) {
                                    if (!entry.getValue().equals(entry.getKey().getLastModified())) {
                                        UndertowScriptLogger.ROOT_LOGGER.rebuildingDueToFileChange(entry.getKey().getPath());
                                        buildEngine();
                                        break;
                                    }
                                }
                                UndertowJS.this.lastHotDeploymentCheck = System.currentTimeMillis();
                            }
                        }
                    }
                }
                if(rejectPaths.contains(exchange.getRelativePath())) {
                    exchange.setResponseCode(StatusCodes.NOT_FOUND);
                    exchange.endExchange();
                    return;
                }
                exchange.putAttachment(NEXT, next);
                routingHandler.handleRequest(exchange);
            }
        };
    }

    public HandlerWrapper getHandlerWrapper() {
        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return getHandler(handler);
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        Builder() {
        }

        private final List<ResourceSet> resources = new ArrayList<>();
        private boolean hotDeployment = true;
        private ClassLoader classLoader = UndertowJS.class.getClassLoader();
        private final Map<String, InjectionProvider> injectionProviders = new HashMap<>();
        private final List<HandlerWrapper> handlerWrappers = new ArrayList<>();
        private ResourceManager resourceManager;
        private final Map<String, TemplateProvider> templateProviders = new HashMap<>();

        public ResourceSet addResourceSet(ResourceManager manager) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resources.add(resourceSet);
            return resourceSet;
        }

        public Builder addResources(ResourceManager manager, String... resources) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resourceSet.addResources(resources);
            this.resources.add(resourceSet);
            return this;
        }

        public Builder addResources(ResourceManager manager, Collection<String> resources) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resourceSet.addResources(resources);
            this.resources.add(resourceSet);
            return this;
        }

        public boolean isHotDeployment() {
            return hotDeployment;
        }

        public Builder setHotDeployment(boolean hotDeployment) {
            this.hotDeployment = hotDeployment;
            return this;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public Builder setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder addInjectionProvider(InjectionProvider provider) {
            this.injectionProviders.put(provider.getPrefix(), provider);
            return this;
        }

        public Builder addHandlerWrapper(HandlerWrapper handlerWrapper) {
            this.handlerWrappers.add(handlerWrapper);
            return this;
        }

        public Builder setResourceManager(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
            return this;
        }

        public Builder addTemplateProvider(TemplateProvider provider) {
            this.templateProviders.put(provider.name(), provider);
            return this;
        }

        public UndertowJS build() {
            return new UndertowJS(resources, hotDeployment, classLoader, injectionProviders, handlerWrappers, resourceManager, templateProviders);
        }
    }

    public static class ResourceSet {

        private final ResourceManager resourceManager;
        private final List<String> resources = new ArrayList<>();

        ResourceSet(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        public ResourceManager getResourceManager() {
            return resourceManager;
        }

        public ResourceSet addResource(String resource) {
            this.resources.add(resource);
            return this;
        }

        public ResourceSet addResources(String... resource) {
            this.resources.addAll(Arrays.asList(resource));
            return this;
        }

        public ResourceSet addResources(Collection<String> resource) {
            this.resources.addAll(resource);
            return this;
        }

        public List<String> getResources() {
            return Collections.unmodifiableList(resources);
        }
    }

    /**
     * class that is used to inspect java objects from scripts
     */
    public static final class JavabeanIntrospector {

        private JavabeanIntrospector() {

        }

        private Map<Class, Map<String, Method>> cache = new ConcurrentHashMap<>();

        public Map<String, Method> inspect(Class<?> clazz) {
            Map<String, Method> existing = cache.get(clazz);
            if (existing != null) {
                return existing;
            }
            existing = new HashMap<>();
            for (Method method : clazz.getMethods()) {
                if (method.isBridge() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getName().equals("getClass")) {
                    continue;
                }
                if (method.getParameterCount() == 0 &&
                        method.getName().startsWith("get") &&
                        method.getName().length() > 3 &&
                        method.getReturnType() != void.class) {
                    existing.put(Character.toLowerCase(method.getName().charAt(3)) + method.getName().substring(4), method);
                }
            }
            cache.put(clazz, existing);
            return existing;
        }

    }

    /**
     * Holder class for objects that undertow needs to access from the script environment
     */
    public static class UndertowSupport {

        private final RoutingHandler routingHandler;
        private final ClassLoader classLoader;
        private final Map<String, InjectionProvider> injectionProviders;
        private final JavabeanIntrospector javabeanIntrospector;
        private final List<HandlerWrapper> handlerWrappers;
        private final ResourceManager resourceManager;
        private final RoutingHandler wsRoutingHandler;
        private final Map<String, TemplateProvider> templateProviders;

        public UndertowSupport(RoutingHandler routingHandler, ClassLoader classLoader, Map<String, InjectionProvider> injectionProviders, JavabeanIntrospector javabeanIntrospector, List<HandlerWrapper> handlerWrappers, ResourceManager resourceManager, RoutingHandler wsRoutingHandler, Map<String, TemplateProvider> templateProviders) {
            this.routingHandler = routingHandler;
            this.classLoader = classLoader;
            this.injectionProviders = injectionProviders;
            this.javabeanIntrospector = javabeanIntrospector;
            this.handlerWrappers = handlerWrappers;
            this.resourceManager = resourceManager;
            this.wsRoutingHandler = wsRoutingHandler;
            this.templateProviders = templateProviders;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public Map<String, InjectionProvider> getInjectionProviders() {
            return injectionProviders;
        }

        public JavabeanIntrospector getJavabeanIntrospector() {
            return javabeanIntrospector;
        }

        public List<HandlerWrapper> getHandlerWrappers() {
            return handlerWrappers;
        }

        public RoutingHandler getRoutingHandler() {
            return routingHandler;
        }

        public ResourceManager getResourceManager() {
            return resourceManager;
        }

        public Map<String, TemplateProvider> getTemplateProviders() {
            return templateProviders;
        }

        public void addWebsocket(String path, WebSocketConnectionCallback callback) {
            wsRoutingHandler.add(Methods.GET, path, new WebSocketProtocolHandshakeHandler(callback, routingHandler));
        }
    }

}
