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

package io.undertow.js.test.json;

import io.undertow.js.InjectionProvider;
import io.undertow.js.UndertowJS;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JSONMappingOfJavaObjectTestCase {
    static UndertowJS js;

    @BeforeClass
    public static void setup() throws Exception {
        js = UndertowJS.builder()
                .addInjectionProvider(new TestDatabaseInjection())
                .addResources(new ClassPathResourceManager(JSONMappingOfJavaObjectTestCase.class.getClassLoader(), JSONMappingOfJavaObjectTestCase.class.getPackage()), "json.js").build();
        js.start();
        DefaultServer.setRootHandler(js.getHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("Default Response");
            }
        }));
    }

    @AfterClass
    public static void after() {
        js.stop();
    }

    @Test
    public void testJsonMappingOfJavaObjects() throws IOException, ScriptException {
        final TestHttpClient client = new TestHttpClient();
        try {

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/bean");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String json = HttpClientUtils.readResponse(result);

            Assert.assertEquals("Bob", js.evaluate("JSON.parse('" + json + "').name"));
            Assert.assertEquals("123 Fake St", js.evaluate("JSON.parse('" + json + "').address"));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/beans");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            json = HttpClientUtils.readResponse(result);

            Assert.assertEquals("Bob", js.evaluate("JSON.parse('" + json + "')[0].name"));
            Assert.assertEquals("123 Fake St", js.evaluate("JSON.parse('" + json + "')[0].address"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public static final class TestDatabaseInjection implements InjectionProvider {

        @Override
        public Object getObject(String name) {
            String[] parts = name.split(":");
            if(parts.length > 2) {
                List<Bean> ret = new ArrayList<>();
                for(int i = 0; i < parts.length; i += 2) {
                    ret.add(new Bean(parts[i], parts[i + 1]));
                }
                return ret;
            }
            return new Bean(parts[0], parts[1]);
        }

        @Override
        public String getPrefix() {
            return "bean";
        }
    }

    public static class Bean {
        private String name, address;

        public Bean(String name, String address) {
            this.name = name;
            this.address = address;
        }

        public Bean() {

        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }
}
