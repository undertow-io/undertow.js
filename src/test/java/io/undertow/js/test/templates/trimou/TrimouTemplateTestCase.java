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

package io.undertow.js.test.templates.trimou;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.script.ScriptException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.js.UndertowJS;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * @author Martin Kouba
 */
@RunWith(DefaultServer.class)
public class TrimouTemplateTestCase {

    @BeforeClass
    public static void setup() throws ScriptException, IOException {

        final ClassPathResourceManager res = new ClassPathResourceManager(TrimouTemplateTestCase.class.getClassLoader(),
                TrimouTemplateTestCase.class.getPackage());
        UndertowJS js = UndertowJS.builder().addResources(res, "trimou.js").setResourceManager(res).build();
        js.start();
        DefaultServer.setRootHandler(js.getHandler(new ResourceHandler(res, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("Default Response");
            }
        })));
    }

    @Test
    public void testSimpleMustacheTemplate() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/testTemplate1");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assertEquals("Template Data: Some Data", HttpClientUtils.readResponse(result));
            assertEquals("text/html; charset=UTF-8", result.getFirstHeader("Content-Type").getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/testTemplate2");
            result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assertEquals("Template Data: Some Data  a1-b1  a2-b2 ", HttpClientUtils.readResponse(result));
            assertEquals("text/plain; charset=UTF-8", result.getFirstHeader("Content-Type").getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSimpleHandlebarsTemplate() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/testTemplate3");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assertEquals("Template Data: Some Data  a1-b1  a2-b2 ", HttpClientUtils.readResponse(result));
            assertEquals("text/plain; charset=UTF-8", result.getFirstHeader("Content-Type").getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
