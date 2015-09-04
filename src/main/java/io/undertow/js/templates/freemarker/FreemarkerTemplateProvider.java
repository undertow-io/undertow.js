package io.undertow.js.templates.freemarker;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class FreemarkerTemplateProvider implements TemplateProvider {

    private volatile Configuration cfg;
    private final Map<String, String> templates = Collections.synchronizedMap(new HashMap<String, String>());

    public String name() {
        return "freemarker";
    }

    @Override
    public void init(Map<String, String> properties) {
        boolean debug = false;
        if (properties.containsKey("debug")) {
            debug = Boolean.parseBoolean(properties.get("debug"));
        }

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
    public Template compile(String templateName, String templateContents) {
        templates.put(templateName, templateContents);
        try {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

    }
}
