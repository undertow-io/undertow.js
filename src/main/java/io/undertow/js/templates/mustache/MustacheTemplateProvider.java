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
