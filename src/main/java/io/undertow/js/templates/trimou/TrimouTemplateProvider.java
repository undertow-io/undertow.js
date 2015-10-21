package io.undertow.js.templates.trimou;

import java.util.Map;

import org.trimou.Mustache;
import org.trimou.engine.MustacheEngine;
import org.trimou.engine.MustacheEngineBuilder;
import org.trimou.engine.config.EngineConfigurationKey;
import org.trimou.handlebars.HelpersBuilder;

import io.undertow.js.templates.Template;
import io.undertow.js.templates.TemplateProvider;

/**
 * TODO Right now, a new provider is created for each handler. Maybe it would be better to cache provider instances.
 *
 * @author Martin Kouba
 */
public class TrimouTemplateProvider implements TemplateProvider {

    private MustacheEngine engine;

    @Override
    public String name() {
        return "trimou";
    }

    @Override
    public void init(Map<String, String> properties) {
        // TODO Currently, it's not possible to use the template cache, i.e. partials and template inheritance will not work - we would have to provide a
        // special template locator, probably using the deployment ResourceManager
        engine = MustacheEngineBuilder.newBuilder().registerHelpers(HelpersBuilder.extra().build())
                .setProperty(EngineConfigurationKey.DEBUG_MODE, Boolean.parseBoolean(properties.get("debug")))
                .setProperty(EngineConfigurationKey.DEFAULT_FILE_ENCODING, properties.containsKey("charset") ? properties.get("charset") : "UTF-8").build();
    }

    @Override
    public Template compile(String templateName, String templateContents) {
        final Mustache mustache = engine.compileMustache(templateName, templateContents);
        return new Template() {
            @Override
            public String apply(Object data) {
                return mustache.render(data);
            }
        };
    }

    @Override
    public void close() {
    }

}
