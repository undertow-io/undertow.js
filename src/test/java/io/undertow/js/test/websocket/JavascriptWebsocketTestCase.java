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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnMessage;
import javax.websocket.Session;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.js.InjectionContext;
import io.undertow.js.InjectionProvider;
import io.undertow.js.UndertowJS;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JavascriptWebsocketTestCase {

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();
    private static final LinkedBlockingDeque<byte[]> binaryMessages = new LinkedBlockingDeque<>();


    static UndertowJS js;

    @BeforeClass
    public static void setup() throws Exception {

        final ClassPathResourceManager res = new ClassPathResourceManager(JavascriptWebsocketTestCase.class.getClassLoader(), JavascriptWebsocketTestCase.class.getPackage());
        js = UndertowJS.builder()
                .addInjectionProvider(new TestInjectionProvider())
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

        byte[] data = new byte[1000];
        for(int i = 0; i < 100; ++i) {
            data[i] = (byte)i;
        }
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
        Assert.assertArrayEquals(data, binaryMessages.poll(5, TimeUnit.SECONDS));
        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "foo"));
    }

    @Test
    public void testWebsocketInjection() throws Exception {
        Session session = ContainerProvider.getWebSocketContainer().connectToServer(ClientEndpointImpl.class, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/websocket2"));
        Assert.assertEquals("INJECTED:a test injection", messages.poll(5, TimeUnit.SECONDS));

        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "foo"));
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
    private static final class TestInjectionProvider implements InjectionProvider {

        @Override
        public Object getObject(InjectionContext injectionContext) {
            return "INJECTED:" + injectionContext.getName();
        }

        @Override
        public String getPrefix() {
            return "test";
        }
    }
}
