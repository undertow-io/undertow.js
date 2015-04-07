undertow.js
===========

Undertow.js is an integration library that provides and easy way to integrate server side JavaScript code with Java EE
components.

This project is in the very early stages, and is not much more than a proof of concept at this stage. Lots of things
can (and almost certainly will) change as the project matures.

This project allows you to register Undertow handlers using javascript, which can inject and use EE components such
as CDI beans. It supports hot deployment, so it is possible to modify your scripts without any compile + redeploy
cycle (especially when combined with the upcoming external resource support in Wildfly). 

Getting Started
---------------

All you need to do to get started is to add undertow.js to your pom:


    <dependency>
        <groupId>io.undertow.js</groupId>
        <artifactId>undertow-js</artifactId>
        <version>1.0.0.Alpha1</version>
    </dependency>

Now you need to add some script files. Undertow.js looks in `WEB-INF/undertow-scripts.conf` for the location of any
script files. This file is a simple text file, that contains the location of server side scripts for Undertow.js to
execute, one per line, e.g.:

    admin.js
    customers.js
    front.js

Now you can register handlers, and inject components into them. Injection takes the form of `provider:name`. At the
moment two providers are supported. CDI allows you to inject CDI beans using `cdi:beanName` and JNDI, which allows you
to inject anything from JNDI using the format `jndi:my-lookup`.

An example of how to register handlers that use injection is below:

    $undertow.onGet("/rest/members", ['cdi:em', function ($exchange, em) {
        $exchange.responseHeaders("content-type", "application/json");
        $exchange.send(JSON.stringify(em.createQuery("select m from Member m order by m.name").getResultList()));
    }]);

The first argument to any handler function is always the exchange, followed by any injections. 

A complete example of the kitchen sink quickstart would look something like the following:


    var Member = Java.type("org.jboss.as.quickstarts.kitchensink.model.Member");
    var ConstraintViolationException = Java.type("javax.validation.ConstraintViolationException");
    var NoResultException = Java.type("javax.persistence.NoResultException");
    
    $undertow
        .wrapper(['jndi:java:comp/UserTransaction', function($exchange, $next, ut) {
            try {
                ut.begin();
                $next();
                ut.commit();
            } catch (e) {
                ut.rollback();
                throw e;
            }
        }])
        .wrapper([function($exchange, $next) {
            try {
                $next();
            } catch (e if e instanceof ConstraintViolationException) {
                var results = {};
                var constraintViolations = Java.from(e.constraintViolations);
                for(i in  constraintViolations) {
                    var cv = constraintViolations[i];
                    results[cv.propertyPath] = cv.message;
                }
                $exchange.send(400,JSON.stringify(results));
            } catch (e) {
                $exchange.send(400, JSON.stringify({"error": e.message}));
                throw e;
            }
        }])
        .onGet("/rest/members", ['cdi:em', function ($exchange, em) {
            $exchange.responseHeaders("content-type", "application/json");
            $exchange.send(JSON.stringify(em.createQuery("select m from Member m order by m.name").getResultList()));
        }])
        .onGet("/rest/members/{id}", ['cdi:em', function ($exchange, em) {
            print(Member.class)
            var member = em.find(Member.class, new java.lang.Long($exchange.param('id'))); //todo: we should be able to make this cleaner
            if (member == null) {
                $exchange.status(404);
            } else {
                $exchange.responseHeaders("content-type", "application/json");
                $exchange.send(JSON.stringify(member));
            }
        }])
        .onPost("/rest/members", ['$entity:json', 'cdi:memberRepository', "cdi:validator", function ($exchange, json, memberRepository, validator) {
    
            var member = $undertow.toJava(Member, json);
            try {
                memberRepository.findByEmail(member.email);
                $exchange.send(409, JSON.stringify({"email": "Email already taken"}));
                return;
            } catch (e if e instanceof NoResultException) {}
    
            var violations = validator.validate(member);
            if (!violations.empty) {
                throw new ConstraintViolationException(violations);
            }
            //you could just use the entity manager directly here
            //this is just a demonstration of how you can call your java code
            memberRepository.save(member)
        }]);