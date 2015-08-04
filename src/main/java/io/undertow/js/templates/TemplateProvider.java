package io.undertow.js.templates;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public interface TemplateProvider extends Closeable, AutoCloseable {

    String name();

    void init(Map<String, String> properties);

    Template compile(String template);

    void close();
}
