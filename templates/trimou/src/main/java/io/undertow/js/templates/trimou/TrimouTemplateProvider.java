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
package io.undertow.js.templates.trimou;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.trimou.engine.MustacheEngine;
import org.trimou.engine.MustacheEngineBuilder;
import org.trimou.engine.config.EngineConfigurationKey;
import org.trimou.engine.locator.AbstractTemplateLocator;
import org.trimou.handlebars.HelpersBuilder;

import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;
import io.undertow.js.templates.Templates;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 *
 * @author Martin Kouba
 */
public class TrimouTemplateProvider implements TemplateProvider {

    private volatile MustacheEngine engine;

    private volatile ResourceManager resourceManager;

    @Override
    public String name() {
        return "trimou";
    }

    @Override
    public void init(Map<String, String> properties, ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        engine = MustacheEngineBuilder
                .newBuilder()
                .registerHelpers(HelpersBuilder.extra().build())
                .addTemplateLocator(new ResourceManagerTemplateLocator())
                .setProperty(EngineConfigurationKey.DEBUG_MODE, Boolean.parseBoolean(properties.get("debug")))
                .setProperty(EngineConfigurationKey.DEFAULT_FILE_ENCODING, properties.containsKey("charset") ? properties.get("charset") : "UTF-8")
                .build();
    }

    @Override
    public Template getTemplate(String templateName) {
        return new Template() {
            @Override
            public String apply(Object data) {
                return engine.getMustache(templateName).render(data);
            }
        };
    }

    private class ResourceManagerTemplateLocator extends AbstractTemplateLocator {

        protected ResourceManagerTemplateLocator() {
            super(1);
        }

        @Override
        public Reader locate(String templateId) {
            try {
                return new StringReader(Templates.loadTemplate(templateId, resourceManager));
            } catch (Exception ignored) {
                return null;
            }
        }

        @Override
        public Set<String> getAllIdentifiers() {
            // Not supported
            return Collections.emptySet();
        }

    }

}
