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

package io.undertow.js.templates.freemarker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;
import io.undertow.js.templates.Templates;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * @author Stuart Douglas
 */
public class FreemarkerTemplateProvider implements TemplateProvider {

    private volatile Configuration cfg;

    private volatile ResourceManager resourceManager;

    private final Map<String, String> templates = Collections.synchronizedMap(new HashMap<String, String>());

    public String name() {
        return "freemarker";
    }

    @Override
    public void init(Map<String, String> properties, ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        boolean debug = Boolean.parseBoolean(properties.get("debug"));
        templates.clear();

        // Create your Configuration instance, and specify if up to what FreeMarker
        // version (here 2.3.22) do you want to apply the fixes that are not 100%
        // backward-compatible. See the Configuration JavaDoc for details.
        cfg = new Configuration(Configuration.VERSION_2_3_22);

        // Set the preferred charset template files are stored in. UTF-8 is
        // a good choice in most applications:
        cfg.setDefaultEncoding(properties.containsKey("charset") ? properties.get("charset") : "UTF-8");

        cfg.setTemplateExceptionHandler(debug ? TemplateExceptionHandler.DEBUG_HANDLER : TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setTemplateLoader(new TemplateLoader() {
            @Override
            public Object findTemplateSource(String name) throws IOException {
                if(!templates.containsKey(name)) {
                    return null;
                }
                return name;
            }

            @Override
            public long getLastModified(Object templateSource) {
                return 0;
            }

            @Override
            public Reader getReader(Object templateSource, String encoding) throws IOException {
                return new StringReader(templates.get(templateSource));
            }

            @Override
            public void closeTemplateSource(Object templateSource) throws IOException {

            }
        });
    }

    @Override
    public Template getTemplate(String templateName) {
        try {
            templates.put(templateName, Templates.loadTemplate(templateName, resourceManager));
            freemarker.template.Template temp = cfg.getTemplate(templateName);
            return new Template() {
                @Override
                public String apply(Object data) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try {
                        temp.process(data, new OutputStreamWriter(out));
                        return new String(out.toByteArray(), cfg.getDefaultEncoding());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
