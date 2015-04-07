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

package io.undertow.js;

import io.undertow.UndertowLogger;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StringReadChannelListener;

import java.io.IOException;

/**
 * Handler that reads the entity body and attaches it to the exchange as a UTF-8 string
 *
 *
 * @author Stuart Douglas
 */
public class StringReadHandler implements HttpHandler {

    public static final AttachmentKey<String> DATA = AttachmentKey.create(String.class);

    private final HttpHandler next;


    public StringReadHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(!exchange.isRequestComplete()) {
            exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
                @Override
                public void run() {
                    new StringReadChannelListener(exchange.getConnection().getBufferPool()) {
                        @Override
                        protected void stringDone(String string) {
                            exchange.putAttachment(DATA, string);
                            Connectors.executeRootHandler(next, exchange);
                        }

                        @Override
                        protected void error(IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            exchange.setResponseCode(500);
                            exchange.endExchange();
                        }
                    }.setup(exchange.getRequestChannel());
                }
            });

        } else {
            next.handleRequest(exchange);
        }


    }
}
