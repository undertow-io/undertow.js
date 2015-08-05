package io.undertow.js;

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

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

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
