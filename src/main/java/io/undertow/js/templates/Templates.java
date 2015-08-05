package io.undertow.js.templates;

import io.undertow.js.UndertowScriptLogger;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

    public static String loadTemplate(String template, ResourceManager resourceManager) throws Exception {
        byte[] buf = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Resource resource = resourceManager.getResource(template);
        if(resource == null) {
            throw UndertowScriptLogger.ROOT_LOGGER.templateNotFound(template);
        }
        try (InputStream stream = resource.getUrl().openStream()) {
            if(stream == null) {
                throw UndertowScriptLogger.ROOT_LOGGER.templateNotFound(template);
            }
            int res;
            while ((res = stream.read(buf)) > 0) {
                out.write(buf, 0, res);
            }
            return out.toString("UTF-8");
        }
    }

}
