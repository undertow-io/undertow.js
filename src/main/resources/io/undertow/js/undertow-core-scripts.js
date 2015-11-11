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
        HandlerWrapper: Java.type("io.undertow.server.HandlerWrapper"),
        EagerFormParsingHandler: Java.type("io.undertow.server.handlers.form.EagerFormParsingHandler"),
        FormDataParser: Java.type("io.undertow.server.handlers.form.FormDataParser"),
        HashMap: Java.type("java.util.HashMap"),
        LinkedList: Java.type("java.util.LinkedList"),
        ServletRequestContext: Java.type("io.undertow.servlet.handlers.ServletRequestContext"),
        WebSockets: Java.type("io.undertow.websockets.core.WebSockets"),
        WebSocketConnectionCallback: Java.type("io.undertow.websockets.WebSocketConnectionCallback"),
        AbstractReceiveListener: Java.type("io.undertow.websockets.core.AbstractReceiveListener"),
        ByteBuffer: Java.type("java.nio.ByteBuffer")
    },

    _allowed_arguments: {'template': true, 'template_type': true, 'headers': true, 'predicate': true, 'roles_allowed': true, 'transactional' : true},

    _injection_aliases: {},
    _entity_parsers: {
        string: function (data) {
            return data;
        },

        json: function (data) {
            return JSON.parse(data);
        },
        form: function(data) {

        }

    },

    default_params: {
        template_type: "mustache",
        headers: {'Content-Type': "text/html; charset=UTF-8"}
    },

    setDefault: function (name, value) {
        if(!$undertow._allowed_arguments[name]) {
            throw "Unknown property " + i;
        }
        $undertow.default_params[name] = value;
        return $undertow;
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

        /**
         * Sets or gets a request header
         *
         *
         * @param name The header name
         * @param value The header value to set. If this is null the current request header is returned
         * @returns {*} The specified request header
         */
        this.requestHeaders = function (name, value) {
            if (arguments.length >= 2) {
                underlyingExchange.requestHeaders.put(new $undertow._java.HttpString(name), value);
            } else if (arguments.length == 1) {
                return underlyingExchange.requestHeaders.getFirst(name);
            } else {
                return underlyingExchange.requestHeaders;
            }
        };

        /**
         * Sets or gets a response header
         *
         *
         * @param name The header name
         * @param value The header value to set. If this is null the current request header is returned
         * @returns {*} The specified request header
         */
        this.responseHeaders = function (name, value) {
            if (arguments.length >= 2) {
                underlyingExchange.responseHeaders.put(new $undertow._java.HttpString(name), value);
            } else if (arguments.length == 1) {
                return underlyingExchange.responseHeaders.getFirst(name);
            } else {
                return underlyingExchange.responseHeaders;
            }
        };

        /**
         * Sends a response. If a number is given as the first parameter this is used as the response code.
         *
         * If the given response is a string it will be sent directly, otherwise it will be converted into JSON.
         */
        this.send = function () {
            var toSend = "";
            if(arguments.length == 1) {
                toSend = arguments[0];
            } else {
                toSend = arguments[1];
                this.status(arguments[0]);
            }
            if(typeof toSend == 'string') {
                underlyingExchange.responseSender.send(toSend);
            } else {
                underlyingExchange.responseSender.send(JSON.stringify(result));
            }
        };

        /**
         * Redirects to a specific location
         *
         * @param location The location to redirect to
         */
        this.sendRedirect = function (location) {
            this.responseHeaders("Location", location);
            this.status(302);
            this.endExchange();
        };

        /**
         * Sets of gets the current response code
         *
         * @returns {*} The current response code
         */
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

        /**
         * Gets a query parameter
         *
         * @param name The query parameter name
         * @returns {*} the query parameter
         */
        this.param = function (name) {
            var paramList = underlyingExchange.queryParameters.get(name);
            if (paramList == null) {
                return null;
            }
            return paramList.getFirst();
        };

        /**
         * Gets a list of query parameters
         *
         * @param name The parameter name
         * @returns {*} A list of query parameters
         */
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

        /**
         * Returns the underlying session object.
         *
         * @param create If the session should be created if it does not exist. Defaults to true
         * @returns {*} The session object
         */
        this.session = function (create) {
            create = create == null ? true : create;
            var src = this.$underlying.getAttachment($undertow._java.ServletRequestContext.ATTACHMENT_KEY);
            return src.getOriginalRequest().getSession(create);
        }

        /**
         *
         * @returns {*} the servlet request
         */
        this.request = function() {
            var src = this.$underlying.getAttachment($undertow._java.ServletRequestContext.ATTACHMENT_KEY);
            return src.getOriginalRequest();
        }

        /**
         *
         * @returns {*} the servlet response
         */
        this.response = function() {
            var src = this.$underlying.getAttachment($undertow._java.ServletRequestContext.ATTACHMENT_KEY);
            return src.getOriginalResponse();
        }
    },

    JDBCWrapper: function ($underlying) {
        this.$underlying = $underlying;

        this.query = function () {
            var conn = null;
            var statement = null;
            conn = $underlying.getConnection();
            statement = conn.prepareStatement(arguments[0]);
            for (var i = 1; i < arguments.length; ++i) {
                statement.setObject(i, arguments[i]);
            }
            return statement.executeUpdate();
        }

        this._select = function (args) {
            var conn = null;
            var statement = null;
            var rs = null;
            conn = $underlying.getConnection();
            statement = conn.prepareStatement(args[0]);
            for (var i = 1; i < args.length; ++i) {
                statement.setObject(i, args[i]);
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
        };

        /**
         *
         */
        this.select = function () {
            return this._select(Array.prototype.slice.call(arguments));
        }

        this.selectOne = function () {
            var result = this._select(Array.prototype.slice.call(arguments));
            if (result.length == 0) {
                return null;
            } else if (result.length > 1) {
                throw "More than one result returned";
            } else {
                return result[0];
            }
        }
    },

    WebSocketConnection: function(underlying) {
        this.$underlying = underlying;
        var $con = this;

        this.send = function(message) {
            if(message == null) {
                throw "Message was null";
            } else if(message.constructor == ArrayBuffer) {
                var view = new Uint8Array(message);
                var buf = $undertow._java.ByteBuffer.allocate(view.length);
                for(var i = 0; i < view.length; ++i) {
                    buf.put(view[i]);
                }
                buf.flip();
                $undertow._java.WebSockets.sendBinary(buf, this.$underlying, null);

            } else if(typeof  message == 'string') {
                $undertow._java.WebSockets.sendText(message, this.$underlying, null);
            } else {
                $con.send(JSON.stringify(message));
            }
        };

        this.onText = null;
        this.onBinary = null;
        this.onClose = null;
        this.onError = null;

        underlying.getReceiveSetter().set(new $undertow._java.AbstractReceiveListener() {
            onFullTextMessage: function(channel, message) {
                if($con.onText != null) {
                    var ret = $con.onText(message.getData());
                    if(ret != null) {
                        $con.send(ret);
                    }
                }
            },
            onFullBinaryMessage: function(channel, message) {

                var data = message.getData();
                var resource = data.getResource();
                try {
                    if ($con.onBinary != null) {
                        var count = 0;
                        for(var i = 0; i < resource.length; ++ i) {
                            count += resource[i].remaining();
                        }
                        var buf = new ArrayBuffer(count);
                        var view = new Uint8Array(buf);
                        count = 0;
                        for(var i = 0; i < resource.length; ++ i) {
                            while(resource[i].hasRemaining()) {
                                view[count++] = resource[i].get();
                            }
                        }

                        var ret = $con.onBinary(buf);
                        if (ret != null) {
                            $con.send(ret);
                        }
                    }
                } finally {
                    data.free();
                }
            },

            onError: function(channel, error) {
                if($con.onError != null) {
                    $con.onError(error);
                }
            },

            onCloseMessage: function(msg, wsChannel) {
                if($con.onClose != null) {
                    $con.onClose(msg);
                }
            }
        });

        underlying.resumeReceives();

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
            return $undertow._injection_aliases[p];
        } else {
            var prefix = p.substr(0, index);
            var suffix = p.substr(index + 1);
            if (prefix == '$entity') {
                if(suffix == 'form') {
                    return function (exchange) {
                        //we handle form encoded data specially
                        //as we rely on Undertow's built in parsers
                        //note that this includes both multipart and form encoded data, we don't differentiate
                        var data = exchange.$underlying.getAttachment($undertow._java.FormDataParser.FORM_DATA);

                        if (data == null) {
                            return data;
                        }
                        //now we turn the data in JS
                        var ret = {};
                        var it = data.iterator();
                        while (it.hasNext()) {
                            var key = it.next();
                            var fv = data.get(key);
                            if (fv.size() == 1) {
                                var item = fv.getFirst();
                                if (item.isFile()) {
                                    ret[key] = {
                                        file: item.file,
                                        headers: item.headers,
                                        fileName: item.fileName
                                    }
                                } else {
                                    ret[key] = item.value;
                                }
                            } else {
                                var itemArray = [];
                                var ii = fv.iterator();
                                while (ii.hasNext()) {
                                    var item = ii.next();
                                    if (item.isFile()) {
                                        itemArray.push({
                                            file: item.file,
                                            headers: item.headers,
                                            fileName: item.fileName
                                        });
                                    } else {
                                        itemArray.push(item.value);
                                    }
                                }
                                ret[key] = itemArray;
                            }
                        }
                        return ret;
                    }
                } else {
                    return function (exchange) {
                        var data = exchange.$underlying.getAttachment($undertow._java.StringReadHandler.DATA);
                        if (suffix == null) {
                            return data;
                        } else {
                            var parser = $undertow._entity_parsers[suffix];
                            if (parser == null) {
                                return data;
                            } else {
                                return parser(data);
                            }
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
                        return provider.getObject($undertow_support.getInjectionContext(suffix));
                    };
                }
            }
        }
    },

    /**
     * Manually resolves an injection
     *
     * @param name The item to resolve
     */
    resolve: function(name) {
        var value =  $undertow._create_injection_function(name)();
        for (var j = 0; j < $undertow.injection_wrappers.length; ++j) {
            value = $undertow.injection_wrappers[j](value);
        }
        return value;
    },

    /**
     * Creates a handler function for a terminal handler
     *
     * @param  userHandler The handler function/array
     * @returns {*} a HttpHandler implementation that can be registered with Undertow
     * @private
     */
    _create_handler_function: function (userHandler, userArgs) {
        if (userHandler == null) {
            throw "handler function cannot be null";
        }
        var handler = userHandler;
        var params = [];
        var args = {};
        for(var i in userArgs) {
            args[i] = userArgs[i];
        }
        for(var i in $undertow.default_params) {
            if(args[i] == null) {
                args[i] = $undertow.default_params[i];
            }
        }
        for(var i in args) {
            if(!$undertow._allowed_arguments[i]) {
                throw "Unknown property " + i;
            }
        }

        if (userHandler.constructor === Array) {
            handler = userHandler[userHandler.length - 1];
            for (var i = 0; i < userHandler.length - 1; ++i) {
                params.push($undertow._create_injection_function(userHandler[i]));
            }
        }
        var template = args["template"];
        var templateInstance = null;

        var headers = args["headers"];
        if(headers == null) {
            headers = {};
        }
        if(template != null) {
            var templateProvider = $undertow_support.templateProviders[args['template_type']];
            templateInstance = templateProvider.getTemplate(template);
            if(headers['Content-Type'] == null) {
                headers['Content-Type'] = $undertow.templateContentType;
            }
        }
        var roles = args['roles_allowed'];
        if(roles != null && !(roles.constructor === Array)) {
            roles = [roles];
        }
        var transactional = args['transactional'];

        var httpHandler = new $undertow._java.HttpHandler({
            handleRequest: function (underlyingExchange) {
                if (underlyingExchange.inIoThread) {
                    underlyingExchange.dispatch(httpHandler);
                    return;
                }
                var $exchange = new $undertow.Exchange(underlyingExchange);

                if(roles != null && roles.length > 0) {
                    var sc = underlyingExchange.getSecurityContext();
                    sc.setAuthenticationRequired();
                    if(!sc.authenticated) {
                        if(!sc.authenticate()) {
                            underlyingExchange.endExchange();
                            return;
                        }
                    }
                    var account = sc.authenticatedAccount;
                    if(account == null) {
                        underlyingExchange.endExchange();
                        return;
                    }
                    var ok = false;
                    for(var i in roles) {
                        var role = roles[i];
                        if(role == '**') {
                            ok = true;
                            break;
                        } else if(account.roles.contains(role)) {
                            ok = true;
                            break;
                        }
                    }
                    if(!ok) {
                        var src = underlyingExchange.getAttachment($undertow._java.ServletRequestContext.ATTACHMENT_KEY);
                        src.originalResponse.sendError(403);
                        return;
                    }

                }

                for(var k in headers) {
                    $exchange.responseHeaders(k, headers[k]);
                }
                var ut = null;
                try {
                    if(transactional) {
                        ut = $undertow.resolve('jndi:java:comp/UserTransaction');
                        ut.begin();
                    }
                    var paramList = [];
                    paramList.push($exchange);
                    $undertow._create_injected_parameter_list(params, paramList, $exchange);
                    var result = handler.apply(null, paramList);
                    if(result != null) {
                        if (template != null) {
                            $exchange.send(templateInstance.apply($undertow.toTemplateData(result)));
                        } else if(typeof result == 'string') {
                            $exchange.send(result);
                        } else {
                            $exchange.send(JSON.stringify(result));
                        }
                    }
                    if(ut != null) {
                        ut.commit();
                    }
                } catch(e) {
                    if(ut != null) {
                        ut.rollback();
                    }
                    throw e;
                }
            }
        });
        for(var i in $undertow._wrappers) {
            httpHandler = $undertow._wrappers[i].wrap(httpHandler);
        }
        for (var i in $undertow_support.handlerWrappers) {
            httpHandler = $undertow_support.handlerWrappers[i].wrap(httpHandler);
        }
        return new $undertow._java.EagerFormParsingHandler().setNext(new $undertow._java.StringReadHandler(httpHandler));
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

    websocket: function(route, userHandler) {

        var handler = userHandler;
        var params = [];
        if (userHandler.constructor === Array) {
            handler = userHandler[userHandler.length - 1];
            for (var i = 0; i < userHandler.length - 1; ++i) {
                params.push($undertow._create_injection_function(userHandler[i]));
            }
        }
        $undertow_support.addWebsocket(route, new $undertow._java.WebSocketConnectionCallback() {
            onConnect: function (exchange, channel) {
                var con = new $undertow.WebSocketConnection(channel);
                var paramList = [];
                paramList.push(con);
                $undertow._create_injected_parameter_list(params, paramList, null);
                handler.apply(null, paramList);
            }
        });
        return $undertow;
    },

    onRequest: function (method, route) {
        if (arguments.length > 3) {
            var args = null;
            var predicate = null;
            if(typeof arguments[2] == 'string') {
                args = {"template": arguments[2]}
            } else {
                args = arguments[2];
                predicate = arguments[2]["predicate"];
            }
            if(predicate != null) {
                $undertow_support.routingHandler.add(method, route, $undertow._java.PredicateParser.parse(predicate, $undertow_support.classLoader), $undertow._create_handler_function(arguments[3], args));
            } else {
                $undertow_support.routingHandler.add(method, route, $undertow._create_handler_function(arguments[3], args));
            }
        } else {
            $undertow_support.routingHandler.add(method, route, $undertow._create_handler_function(arguments[2], {}));
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
        $undertow._injection_aliases[alias] = $undertow._create_injection_function(injection);
        return $undertow;
    },

    toJava: function (type, val) {
        var m = new type();
        for (var n in val) {
            //todo: complex graphs
            m[n] = val[n];
        }
        return m;
    },

    toTemplateData: function (val) {

        if(Array.isArray(val)) {
            var list = new $undertow._java.LinkedList();
            for (var i in val) {
                list.add($undertow.toTemplateData(val[i]));
            }
            return list;
        } else if (typeof val == 'object') {
            var map = new $undertow._java.HashMap();
            var keys = Object.keys(val);
            for (var i in keys) {
                map.put(keys[i], $undertow.toTemplateData(val[keys[i]]));
            }
            return map;
        } else {
            return val;
        }
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

