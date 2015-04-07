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
    .alias("db", "db:test")
    .onPost("/customers",['db', '$entity:json', function ($exchange, db, customer) {
        db.query("insert into customer(first, last) values (?, ?)", customer.first, customer.last);
    }])
    .onPut("/customers/{id}",['db', '$entity:json', function ($exchange, db, customer) {
        db.query("update customer set first=?, last=? where id=?", customer['first'], customer['last'], $exchange.param('id'));
    }])
    .onDelete("/customers/{id}",['db', function ($exchange, db) {
        db.query("delete from customer where id=?", $exchange.param('id'));
    }])
    .onGet("/customers/{id}",['db', function ($exchange, db) {
        var customer = db.selectOne("select * from customer where id=?", $exchange.param('id'));
        if(customer != null) {
            $exchange.send(JSON.stringify(customer));
        } else {
            $exchange.status(404);
        }
        $exchange.send(JSON.stringify(customer));
    }])
    .onGet("/customers",['db', function ($exchange, db) {
        var customers = db.select("select * from customer");
        $exchange.send(JSON.stringify(customers));
    }]);