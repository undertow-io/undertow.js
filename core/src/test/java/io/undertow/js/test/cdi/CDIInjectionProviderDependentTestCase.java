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
package io.undertow.js.test.cdi;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.script.ScriptException;
import javax.servlet.ServletException;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jboss.weld.environment.Container;
import org.jboss.weld.environment.servlet.Listener;
import org.jboss.weld.environment.undertow.UndertowContainer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.js.UndertowJS;
import io.undertow.js.providers.cdi.CDIInjectionProvider;
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
public class CDIInjectionProviderDependentTestCase {

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
    private static final LinkedBlockingDeque<byte[]> binaryMessages = new LinkedBlockingDeque<>();


    private static UndertowJS undertowJs;
    private static DeploymentManager manager;
    @BeforeClass
    public static void setup() throws ScriptException, IOException, ServletException {
        undertowJs = UndertowJS.builder().addInjectionProvider(new CDIInjectionProvider())
                .addResources(new ClassPathResourceManager(CDIInjectionProviderDependentTestCase.class.getClassLoader(),
                        CDIInjectionProviderDependentTestCase.class.getPackage()), "cdi_dependent_bean.js")
                .build();
        undertowJs.start();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo().setClassLoader(CDIInjectionProviderDependentTestCase.class.getClassLoader())
                .addListener(Servlets.listener(Listener.class)).addInitParameter(Container.CONTEXT_PARAM_CONTAINER_CLASS, UndertowContainer.class.getName())
                .setContextPath("/cdi").setDeploymentName("cdiinject.war").addInnerHandlerChainWrapper(new HandlerWrapper() {
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
        manager.stop();
        manager.undeploy();
        undertowJs.stop();
    }

    @After
    public void after() {
        Bar.DESTROYED.clear();
    }

    @Test
    public void testDependentBean() throws IOException, ScriptException, ServletException {

        final TestHttpClient client = new TestHttpClient();
        try {
            testRequest(client);
            testRequest(client);
            testRequest(client);
            assertEquals(Bar.DESTROYED.size(), 3);
        } finally {
            client.getConnectionManager().shutdown();
        }

    }

    @Test
    public void testWebsocketInjection() throws Exception {
        Session session = ContainerProvider.getWebSocketContainer().connectToServer(ClientEndpointImpl.class, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/cdi/websocket"));
        Assert.assertEquals("Barpong", messages.poll(5, TimeUnit.SECONDS));

        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "foo"));
        Thread.sleep(1000);
        assertEquals(Bar.DESTROYED.size(), 1);
    }

    @ClientEndpoint
    private static class ClientEndpointImpl {

        @OnMessage
        public void message(String message) {
            messages.add(message);
        }

        @OnMessage
        public void message(byte[] message) {
            binaryMessages.add(message);
        }

    }

    private void testRequest(TestHttpClient client) throws ClientProtocolException, IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/cdi/foo");
        CloseableHttpResponse result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        assertEquals("Barpong", HttpClientUtils.readResponse(result));
    }

}
