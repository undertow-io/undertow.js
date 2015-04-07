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

$undertow
    .alias("json", "$entity:json")
    .wrapper("path[/testWrapper]", ["test:my-wrapper",function($exchange, $next, value) {
        $exchange.responseHeaders("Wrapper", value);
        $next();
    }])
    .onGet("/testResponseSender", function ($exchange) {
        $exchange.send("Response Sender");
    })
    .onGet("/testRequestHeaders", function ($exchange) {
        $exchange.send($exchange.requestHeaders("my-header"));
    })
    .onGet("/testResponseHeaders", function ($exchange) {
        $exchange.responseHeaders("my-header", "my-header-value");
    })
    .onGet("/testSendRedirect", function($exchange) {
        $exchange.sendRedirect("/testResponseSender");
    })
    .onGet("/testPredicatedHandlers", "equals[%{i,my-header}, foo]", function ($exchange) {
        $exchange.send("Foo Header");
    })
    .onGet("/testPredicatedHandlers", function ($exchange) {
        $exchange.send("No match");
    })
    .onGet("/testParams/{id}", function ($exchange) {
        $exchange.send("ID " + $exchange.params('id')[0]);
    })
    .onGet("/testSimpleInjection", ["test:my-injection", function($exchange, invection) {
        $exchange.send(invection);
    }])
    .onPost("/testEntityInjection", ['$entity:string', function($exchange, entity) {
        $exchange.send(201, entity);
    }])
    .onPost("/testEntityJsonInjection", "equals[%{i,content-type}, text/json]", ['json', function($exchange, entity) {
        $exchange.send(entity['first']);
    }])
    .onGet("/testWrapper", function($exchange) {
        $exchange.send("wrapper");
    });

