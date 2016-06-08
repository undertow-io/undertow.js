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

package io.undertow.js.test.session;

import io.undertow.js.UndertowJS;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SessionTestCase {
    static UndertowJS js;

    @BeforeClass
    public static void setup() throws Exception {
        js = UndertowJS.builder()
                .addResources(new ClassPathResourceManager(SessionTestCase.class.getClassLoader(), SessionTestCase.class.getPackage()), "session.js").build();
        js.start();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SessionTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .addInnerHandlerChainWrapper(new HandlerWrapper() {
                    @Override
                    public HttpHandler wrap(HttpHandler handler) {
                        return js.getHandler(handler);
                    }
                });

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        PathHandler root = new PathHandler();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);

    }

    @AfterClass
    public static void after() {
        js.stop();
    }

    @Test
    public void testSession() throws IOException, ScriptException {
        final TestHttpClient client = new TestHttpClient();
        try {

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/count");
            CloseableHttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assertEquals("1", HttpClientUtils.readResponse(result));


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/count");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assertEquals("2", HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
