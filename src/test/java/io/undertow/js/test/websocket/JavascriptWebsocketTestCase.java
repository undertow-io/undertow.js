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

package io.undertow.js.test.websocket;

import io.undertow.js.UndertowJS;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.net.URI;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JavascriptWebsocketTestCase {

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

    static UndertowJS js;

    @BeforeClass
    public static void setup() throws Exception {

        final ClassPathResourceManager res = new ClassPathResourceManager(JavascriptWebsocketTestCase.class.getClassLoader(), JavascriptWebsocketTestCase.class.getPackage());
        js = UndertowJS.builder()
                .addResources(res, "websocket.js")
                .setResourceManager(res).build();
        js.start();
        DefaultServer.setRootHandler(js.getHandler(new ResourceHandler(res, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("Default Response");
            }
        })));

    }

    @AfterClass
    public static void after() {
        js.stop();
    }

    @Test
    public void testWebsockets1() throws Exception {
        Session session = ContainerProvider.getWebSocketContainer().connectToServer(ClientEndpointImpl.class, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/websocket1"));
        Assert.assertEquals("connected", messages.poll(5, TimeUnit.SECONDS));
        session.getBasicRemote().sendText("test1");
        Assert.assertEquals("echo-test1", messages.poll(5, TimeUnit.SECONDS));
        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "foo"));
    }

    @ClientEndpoint
    private static class ClientEndpointImpl {

        @OnMessage
        public void message(String message) {
            messages.add(message);
        }

    }
}
