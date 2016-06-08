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

package io.undertow.js;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import javax.servlet.ServletContext;

import io.undertow.js.templates.TemplateProvider;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.FileUtils;

/**
 * @author Stuart Douglas
 */
public class UndertowJSServletExtension implements ServletExtension {

    private final String SCRIPTS_FILE = "WEB-INF/undertow-scripts.conf";

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
        try {
            ResourceManager resourceManager = deploymentInfo.getResourceManager();
            if (resourceManager == null) {
                return;
            }
            Resource scripts = resourceManager.getResource(SCRIPTS_FILE);
            if (scripts == null) {
                return;
            }

            try (InputStream data = scripts.getUrl().openStream()) {

                UndertowJS.Builder builder = UndertowJS.builder();
                List<String> files = new ArrayList<>();

                String contents = FileUtils.readFile(data);
                String[] lines = contents.split("\n");
                for (String line : lines) {
                    String trimmed = line;
                    int commentIndex = trimmed.indexOf("#");
                    if (commentIndex > -1) {
                        trimmed = trimmed.substring(0, commentIndex);
                    }
                    trimmed = trimmed.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    Resource path = resourceManager.getResource(trimmed);
                    if (path != null) {
                        files.add(trimmed);
                    } else {
                        throw UndertowScriptLogger.ROOT_LOGGER.couldNotFileScript(trimmed);
                    }
                }
                builder.addResources(resourceManager, files);

                ClassLoader classLoader = deploymentInfo.getClassLoader() != null ? deploymentInfo.getClassLoader() : getClass().getClassLoader();
                ServiceLoader<InjectionProvider> loader = ServiceLoader.load(InjectionProvider.class, classLoader);
                for(InjectionProvider provider : loader) {
                    builder.addInjectionProvider(provider);
                }
                Iterator<TemplateProvider> iterator = ServiceLoader.load(TemplateProvider.class, classLoader).iterator();
                while (iterator.hasNext()) {
                    try {
                        builder.addTemplateProvider(iterator.next());
                    } catch (ServiceConfigurationError ignored) {
                        // Catching errors is bad but this should be safe
                    }
                }

                builder.addHandlerWrapper(new HandlerWrapper() {
                    @Override
                    public HttpHandler wrap(final HttpHandler handler) {
                        return new HttpHandler() {
                            @Override
                            public void handleRequest(HttpServerExchange exchange) throws Exception {
                                ServletRequestContext current;
                                if(System.getSecurityManager() != null) {
                                    current = AccessController.doPrivileged(new PrivilegedAction<ServletRequestContext>() {
                                        @Override
                                        public ServletRequestContext run() {
                                            return ServletRequestContext.current();
                                        }
                                    });
                                } else {
                                    current = ServletRequestContext.current();
                                }
                                if(current == null) {
                                    ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                                    if(src != null) {
                                        ThreadSetupAction.Handle handle = src.getDeployment().getThreadSetupAction().setup(exchange);
                                        try {
                                            handler.handleRequest(exchange);
                                        } finally {
                                            handle.tearDown();
                                        }
                                    } else {
                                        handler.handleRequest(exchange);
                                    }
                                } else {
                                    handler.handleRequest(exchange);
                                }
                            }
                        };
                    }
                });
                builder.setHotDeployment(true); //todo: configurable?
                builder.setClassLoader(classLoader);
                builder.setResourceManager(resourceManager);
                UndertowJS js = builder.build();
                js.start();

                deploymentInfo.addInnerHandlerChainWrapper(js.getHandlerWrapper());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
