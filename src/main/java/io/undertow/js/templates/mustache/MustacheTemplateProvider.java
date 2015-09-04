package io.undertow.js.templates.mustache;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class MustacheTemplateProvider implements TemplateProvider {

    public String name() {
        return "mustache";
    }

    @Override
    public void init(Map<String, String> properties) {

    }

    @Override
    public Template compile(String templateName, String template) {
        MustacheFactory mf = new DefaultMustacheFactory();
        final Mustache mustache = mf.compile(new StringReader(template), templateName);

        return new Template() {
            @Override
            public String apply(Object data) {
                final StringWriter stringWriter = new StringWriter();
                mustache.execute(stringWriter, data);
                return stringWriter.getBuffer().toString();
            }
        };
    }

    @Override
    public void close() {

    }
}
