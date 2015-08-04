package io.undertow.js.templates;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author Stuart Douglas
 */
public class Templates {

    public static TemplateProvider loadTemplateProvider(ClassLoader classLoader, String name) {
        ServiceLoader<TemplateProvider> sl = ServiceLoader.load(TemplateProvider.class, classLoader);
        for(TemplateProvider prov : sl) {
            if(prov.name().equals(name)) {
                return prov;
            }
        }
        return null;
    }

    public static String loadTemplate(String template, ClassLoader classLoader) throws Exception {
        byte[] buf = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream stream = classLoader.getResourceAsStream(template)) {
            int res;
            while ((res = stream.read(buf)) > 0) {
                out.write(buf, 0, res);
            }
            return out.toString("UTF-8");
        }
    }

}
