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

package io.undertow.js.test.jdbc;

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
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JavascriptJDBCWrapperTestCase {
    static JdbcConnectionPool ds;
    static UndertowJS js;

    @BeforeClass
    public static void setup() throws Exception {
        ds = JdbcConnectionPool.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "password");

        Connection conn = null;
        Statement statement = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(true);
            statement = conn.createStatement();
            statement.executeUpdate("CREATE TABLE PUBLIC.CUSTOMER (" +
                    " id SERIAL NOT NULL," +
                    " first VARCHAR(255) NOT NULL," +
                    " last VARCHAR(255)," +
                    " PRIMARY KEY (id)" +
                    " );");
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (conn != null) {
                conn.close();
            }

        }


        js = UndertowJS.builder()
                .addInjectionProvider(new TestDatabaseInjection())
                .addResources(new ClassPathResourceManager(JavascriptJDBCWrapperTestCase.class.getClassLoader(), JavascriptJDBCWrapperTestCase.class.getPackage()), "jdbc.js").build();
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
        ds.dispose();
        js.stop();
    }

    @Test
    public void testDatabaseCRUDOperations() throws IOException, ScriptException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/customers");
            post.setEntity(new StringEntity("{\"first\": \"John\", \"last\": \"Doe\"}"));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/customers");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String json = HttpClientUtils.readResponse(result);

            Assert.assertEquals("John", js.evaluate("JSON.parse('" + json + "')[0].FIRST"));
            Assert.assertEquals("Doe", js.evaluate("JSON.parse('" + json + "')[0].LAST"));
            String id = js.evaluate("JSON.parse('" + json + "')[0].ID").toString();
            Assert.assertNotNull(id);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/customers/" + id);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            json = HttpClientUtils.readResponse(result);

            Assert.assertEquals("John", js.evaluate("JSON.parse('" + json + "').FIRST"));
            Assert.assertEquals("Doe", js.evaluate("JSON.parse('" + json + "').LAST"));
            id = js.evaluate("JSON.parse('" + json + "').ID").toString();
            Assert.assertNotNull(id);

            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/customers/" + id);
            put.setEntity(new StringEntity("{\"first\": \"John\", \"last\": \"Smith\"}"));
            result = client.execute(put);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/customers/" + id);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            json = HttpClientUtils.readResponse(result);

            Assert.assertEquals("John", js.evaluate("JSON.parse('" + json + "').FIRST"));
            Assert.assertEquals("Smith", js.evaluate("JSON.parse('" + json + "').LAST"));
            id = js.evaluate("JSON.parse('" + json + "').ID").toString();
            Assert.assertNotNull(id);


            HttpDelete delete = new HttpDelete(DefaultServer.getDefaultServerURL() + "/customers/" + id);
            result = client.execute(delete);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/customers/" + id);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            json = HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public static final class TestDatabaseInjection implements InjectionProvider {

        @Override
        public Object getObject(String name) {
            return ds;
        }

        @Override
        public String getPrefix() {
            return "db";
        }
    }
}
