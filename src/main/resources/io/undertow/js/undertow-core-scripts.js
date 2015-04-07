"use strict";
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

/**
 * Undertow scripts that provide core javascript functionality
 */


var $undertow = {
    _java: {
        HttpHandler: Java.type("io.undertow.server.HttpHandler"),
        HttpString: Java.type("io.undertow.util.HttpString"),
        PredicateParser: Java.type("io.undertow.predicate.PredicateParser"),
        PredicateHandler: Java.type("io.undertow.server.handlers.PredicateHandler"),
        StringReadHandler: Java.type("io.undertow.js.StringReadHandler"),
        DataSource: Java.type("javax.sql.DataSource"),
        HandlerWrapper: Java.type("io.undertow.server.HandlerWrapper")
    },

    injection_aliases: {},
    entity_parsers: {
        string: function (data) {
            return data;
        },

        json: function (data) {
            return JSON.parse(data);
        }
    },

    injection_wrappers: [
        /**
         * JDBC wrapper function. Wraps injected datasources with our JS friendly database API
         * @param injected
         */
            function (injected) {
            if (injected instanceof $undertow._java.DataSource) {
                return new $undertow.JDBCWrapper(injected);
            }
            return injected;
        }

    ],

    /**
     * filters
     */
    _wrappers: [],

    Exchange: function (underlyingExchange) {

        this.$underlying = underlyingExchange;

        this.requestHeaders = function (name, value) {
            if (arguments.length >= 2) {
                underlyingExchange.requestHeaders.put(new $undertow._java.HttpString(name), value);
            } else if (arguments.length == 1) {
                return underlyingExchange.requestHeaders.getFirst(name);
            } else {
                //TODO: return some kind of headers object
            }
        };

        this.responseHeaders = function () {
            if (arguments.length >= 2) {
                underlyingExchange.responseHeaders.put(new $undertow._java.HttpString(arguments[0]), arguments[1]);
            } else if (arguments.length == 1) {
                return underlyingExchange.responseHeaders.getFirst(arguments[1]);
            } else {
                //TODO: return some kind of headers object
            }
        };

        this.send = function () {
            var toSend = "";
            if(arguments.length == 1) {
                toSend = arguments[0];
            } else {
                toSend = arguments[1];
                this.status(arguments[0]);
            }
            underlyingExchange.responseSender.send(toSend);
        };

        this.sendRedirect = function (location) {
            this.responseHeaders("Location", location);
            this.status(302);
            this.endExchange();
        };

        this.status = function () {
            if (arguments.length > 0) {
                underlyingExchange.setResponseCode(arguments[0]);
            } else {
                return underlyingExchange.responseCode;
            }
        };

        this.endExchange = function () {
            underlyingExchange.endExchange();
        };

        this.param = function (name) {
            var paramList = underlyingExchange.queryParameters.get(name);
            if (paramList == null) {
                return null;
            }
            return paramList.getFirst();
        };

        this.params = function (name) {
            var params = underlyingExchange.queryParameters.get(name);
            if (params == null) {
                return null;
            }
            var it = params.iterator();
            var ret = [];
            while (it.hasNext()) {
                ret.push(it.next());
            }
            return ret;
        };
    },

    JDBCWrapper: function ($underlying) {
        this.$underlying = $underlying;

        this.query = function () {
            var conn = null;
            var statement = null;
            try {
                conn = $underlying.getConnection();
                conn.setAutoCommit(true); //TODO: how to deal with transactions?
                statement = conn.prepareStatement(arguments[0]);
                for (var i = 1; i < arguments.length; ++i) {
                    statement.setObject(i, arguments[i]);
                }
                statement.execute();
            } finally {
                if (statement != null) {
                    statement.close();
                }
                if (conn != null) {
                    conn.close();
                }

            }
        }

        this.select = function () {
            var conn = null;
            var statement = null;
            var rs = null;
            try {
                conn = $underlying.getConnection();
                conn.setAutoCommit(true); //TODO: how to deal with transactions?
                statement = conn.prepareStatement(arguments[0]);
                for (var i = 1; i < arguments.length; ++i) {
                    statement.setObject(i, arguments[i]);
                }

                rs = statement.executeQuery();
                var ret = [];
                var md = rs.getMetaData();
                var columnCount = md.getColumnCount();
                var types = {};
                var names = {};
                for (var i = 1; i <= columnCount; ++i) {
                    types[i] = md.getColumnClassName(i);
                    names[i] = md.getColumnName(i);
                }
                while (rs.next()) {
                    var rec = {};
                    for (var j = 1; j <= columnCount; ++j) {
                        var name = names[j];
                        var type = types[j];
                        switch (type) {
                            case "java.lang.String":
                                rec[name] = rs.getString(j);
                                break;
                            case "java.lang.Integer":
                                rec[name] = rs.getInt(j);
                                break;
                            case "java.lang.Double":
                                rec[name] = rs.getDouble(j);
                                break;
                            case "java.lang.Float":
                                rec[name] = rs.getFloat(j);
                                break;
                            case "java.lang.Boolean":
                                rec[name] = rs.getBoolean(j);
                                break;
                            case "java.lang.Long":
                                rec[name] = rs.getLong(j);
                                break;
                            case "java.lang.Short":
                                rec[name] = rs.getShort(j);
                                break;
                            case "java.lang.Byte":
                                rec[name] = rs.getByte(j);
                                break;
                            case "java.sql.Date":
                                rec[name] = rs.getDate(j);
                                break;
                            case "java.sql.Time":
                                rec[name] = rs.getTime(j);
                                break;
                            default :
                                rec[name] = rs.getString(j);
                        }
                    }
                    ret.push(rec)
                }
                rs.close();
                statement.close();
                conn.close();
                return ret;
            } catch (e) {
                if (rs != null) {
                    rs.close();
                }
                if (statement != null) {
                    statement.close();
                }
                if (conn != null) {
                    conn.close();
                }
            }
        };

        this.selectOne = function () {

            var result = this.select.apply(null, arguments);
            if (result.length == 0) {
                return null;
            } else if (result.length > 1) {
                throw "More than one result returned";
            } else {
                return result[0];
            }
        }
    },

    /**
     * Create an injection function from a given injection string
     *
     * @param p the injection string
     * @returns {*} a function that can be invoked to get the object to be injected
     * @private
     */
    _create_injection_function: function (p) {
        var index = p.indexOf(":");
        if (index < 0) {
            //no prefix, it has to be an alias
            //we just use the alias function directly
            return $undertow.injection_aliases[p];
        } else {
            var prefix = p.substr(0, index);
            var suffix = p.substr(index + 1);
            if (prefix == '$entity') {
                return function (exchange) {
                    var data = exchange.$underlying.getAttachment($undertow._java.StringReadHandler.DATA);
                    if (suffix == null) {
                        return data;
                    } else {
                        var parser = $undertow.entity_parsers[suffix];
                        if (parser == null) {
                            return data;
                        } else {
                            return parser(data);
                        }
                    }
                }
            } else {
                var provider = $undertow_support.injectionProviders[prefix];
                if (provider == null) {
                    return function () {
                        return null;
                    };
                } else {
                    return function () {
                        return provider.getObject(suffix);
                    };
                }
            }
        }
    },

    /**
     * Creates a handler function for a terminal handler
     *
     * @param  userHandler The handler function/array
     * @returns {*} a HttpHandler implementation that can be registered with Undertow
     * @private
     */
    _create_handler_function: function (userHandler) {
        if (userHandler == null) {
            throw "handler function cannot be null";
        }
        var handler = userHandler;
        var params = [];
        if (userHandler.constructor === Array) {
            handler = userHandler[userHandler.length - 1];
            for (var i = 0; i < userHandler.length - 1; ++i) {
                params.push($undertow._create_injection_function(userHandler[i]));
            }
        }
        var httpHandler = new $undertow._java.HttpHandler({
            handleRequest: function (underlyingExchange) {
                if (underlyingExchange.inIoThread) {
                    underlyingExchange.dispatch(httpHandler);
                    return;
                }

                var $exchange = new $undertow.Exchange(underlyingExchange);

                var paramList = [];
                paramList.push($exchange);
                $undertow._create_injected_parameter_list(params, paramList, $exchange);
                handler.apply(null, paramList);
            }
        });
        for(var i in $undertow._wrappers) {
            httpHandler = $undertow._wrappers[i].wrap(httpHandler);
        }
        for (var i in $undertow_support.handlerWrappers) {
            httpHandler = $undertow_support.handlerWrappers[i].wrap(httpHandler);
        }
        return new $undertow._java.StringReadHandler(httpHandler);
    },

    /**
     *
     * @param params The list of injection provider functions
     * @param paramList The array of actual values to inject
     * @param $exchange The current exchange wrapper
     * @private
     */
    _create_injected_parameter_list: function (params, paramList, $exchange) {
        for (var i = 0; i < params.length; ++i) {
            var param = params[i];
            if (param == null) {
                paramList.push(null);
            } else {
                var toInject = param($exchange);
                for (var j = 0; j < $undertow.injection_wrappers.length; ++j) {
                    toInject = $undertow.injection_wrappers[j](toInject);
                }
                paramList.push(toInject);
            }
        }
    },

    onGet: function () {
        var args = ["GET"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onPost: function (route, handler) {
        var args = ["POST"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onPut: function (route, handler) {
        var args = ["PUT"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onDelete: function (route, handler) {
        var args = ["DELETE"];
        for (var i = 0; i < arguments.length; ++i) {
            args.push(arguments[i]);
        }
        $undertow.onRequest.apply(null, args);
        return $undertow;
    },

    onRequest: function (method, route) {
        if (arguments.length > 3) {
            $undertow_support.routingHandler.add(method, route, $undertow._java.PredicateParser.parse(arguments[2], $undertow_support.classLoader), $undertow._create_handler_function(arguments[3]));
        } else {
            $undertow_support.routingHandler.add(method, route, $undertow._create_handler_function(arguments[2]));
        }

        return $undertow;
    },


    wrapper: function () {
        var predicate = null;
        var userHandler = null;
        if (arguments.length == 1) {
            userHandler = arguments[0];
        } else {
            predicate = arguments[0];
            userHandler = arguments[1];
        }
        if (predicate != null) {
            predicate = $undertow._java.PredicateParser.parse(predicate, $undertow_support.classLoader);
        }

        var handler = userHandler;
        var params = [];
        if (userHandler.constructor === Array) {
            handler = userHandler[userHandler.length - 1];
            for (var i = 0; i < userHandler.length - 1; ++i) {
                params.push($undertow._create_injection_function(userHandler[i]));
            }
        }
        $undertow._wrappers.push(new $undertow._java.HandlerWrapper({

            wrap: function (next) {

                var filterHttpHandler = new $undertow._java.HttpHandler({
                    handleRequest: function (underlyingExchange) {

                        if (underlyingExchange.inIoThread) {
                            underlyingExchange.dispatch(filterHttpHandler);
                            return;
                        }
                        //TODO: re-use this between filters and handlers
                        var $exchange = new $undertow.Exchange(underlyingExchange);

                        var paramList = [];
                        paramList.push($exchange);
                        paramList.push(function () {
                            next.handleRequest(underlyingExchange);
                        });
                        $undertow._create_injected_parameter_list(params, paramList, $exchange);
                        handler.apply(null, paramList);
                    }

                });
                if (predicate == null) {
                    return  filterHttpHandler;
                } else {
                    return new $undertow._java.PredicateHandler(predicate, filterHttpHandler, next);
                }
            }

        }));
        return $undertow;
    },

    alias: function (alias, injection) {
        $undertow.injection_aliases[alias] = $undertow._create_injection_function(injection);
        return $undertow;
    },

    toJava: function (type, val) {
        var m = new type();
        for (var n in val) {
            //todo: complex graphs
            m[n] = val[n];
        }
        return m;
    }
};


//setup the JSON stringifyer to handle java object
$undertow._oldStringify = JSON.stringify;
JSON.stringify = function (value, replacer, space) {
    var newReplacer = function (name, value) {
        if (value == null) {
            return replacer == null ? null : replacer(name, null);
        }
        if (value instanceof Object) {
            return replacer == null ? value : replacer(name, value);
        }
        if (typeof value != 'object') {
            return replacer == null ? value : replacer(name, value);
        }
        if (value instanceof java.util.Collection) {
            return replacer == null ? Java.from(value) : replacer(name, Java.from(value));
        }
        var ret = {};
        var methodMap = $undertow_support.javabeanIntrospector.inspect(value.class);
        for (name in methodMap) {
            ret[name] = methodMap[name].invoke(value, []);
        }
        return replacer == null ? ret : replacer(name, ret);
    }
    return $undertow._oldStringify(value, newReplacer, space);

};