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

package io.undertow.js.templates.mustache;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;
import io.undertow.js.templates.Templates;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * @author Stuart Douglas
 */
public class MustacheTemplateProvider implements TemplateProvider {

    private volatile ResourceManager resourceManager;

    public String name() {
        return "mustache";
    }

    @Override
    public void init(Map<String, String> properties, ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public Template getTemplate(String templateName) {
        try {
            MustacheFactory mf = new DefaultMustacheFactory();
            final Mustache mustache = mf.compile(new StringReader(Templates.loadTemplate(templateName, resourceManager)), templateName);

            return new Template() {
                @Override
                public String apply(Object data) {
                    final StringWriter stringWriter = new StringWriter();
                    mustache.execute(stringWriter, data);
                    return stringWriter.getBuffer().toString();
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
