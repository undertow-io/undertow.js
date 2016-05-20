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
package io.undertow.js.test.templates.trimou;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.script.ScriptException;
import javax.servlet.ServletException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jboss.weld.environment.Container;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.environment.servlet.Listener;
import org.jboss.weld.environment.servlet.WeldServletLifecycle;
import org.jboss.weld.environment.undertow.UndertowContainer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.js.UndertowJS;
import io.undertow.js.templates.trimou.TrimouTemplateProvider;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 *
 * @author Martin Kouba
 *
 */
@RunWith(DefaultServer.class)
public class TrimouExtensionsTemplateTestCase {

    private static UndertowJS undertowJs;

    private static DeploymentManager manager;

    private static WeldContainer weldContainer;

    @BeforeClass
    public static void setup()
            throws ScriptException, IOException, ServletException {

        ClassPathResourceManager resourceManager = new ClassPathResourceManager(
                TrimouTemplateTestCase.class.getClassLoader(),
                TrimouExtensionsTemplateTestCase.class.getPackage());

        // CDI extension expects the container to be already started
        weldContainer = new Weld().initialize();

        undertowJs = UndertowJS.builder()
                .addTemplateProvider(new TrimouTemplateProvider())
                .addResources(resourceManager, "trimou.js")
                .setResourceManager(resourceManager).build();
        undertowJs.start();

        ServletContainer container = ServletContainer.Factory
                .newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(
                        TrimouExtensionsTemplateTestCase.class.getClassLoader())
                .addListener(Servlets.listener(Listener.class))
                .addInitParameter(Container.CONTEXT_PARAM_CONTAINER_CLASS,
                        UndertowContainer.class.getName())
                // Reuse the BeanManager from Weld SE
                .addServletContextAttribute(
                        WeldServletLifecycle.BEAN_MANAGER_ATTRIBUTE_NAME,
                        weldContainer.getBeanManager())
                .setContextPath("/ext")
                .setDeploymentName("trimou-extensions.war")
                .addInnerHandlerChainWrapper(new HandlerWrapper() {
                    @Override
                    public HttpHandler wrap(HttpHandler handler) {
                        return undertowJs.getHandler(handler);
                    }
                });

        manager = container.addDeployment(builder);
        manager.deploy();
        PathHandler root = new PathHandler();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @AfterClass
    public static void teardown() throws ServletException {
        weldContainer.shutdown();
        manager.stop();
        manager.undeploy();
        undertowJs.stop();
    }

    @Test
    public void testExtensions()
            throws IOException, ScriptException, ServletException {
        try (TestHttpClient client = new TestHttpClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL()
                    + "/ext/testTemplateExtensions");
            CloseableHttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK,
                    result.getStatusLine().getStatusCode());
            assertEquals("pong", HttpClientUtils.readResponse(result).trim());
        }
    }

}
