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
package io.undertow.js.templates;

import java.util.Map;

import io.undertow.js.UndertowJS;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * @author Stuart Douglas
 * @author Martin Kouba
 */
public interface TemplateProvider {

    /**
     *
     * @return the name used in the <code>template_type</code> param
     */
    String name();

    /**
     * The provider may be reinitialized during {@link UndertowJS} rebuild (hot deployment).
     *
     * @param properties
     * @param resourceManager
     */
    default void init(Map<String, String> properties, ResourceManager resourceManager) {
    }

    /**
     * The provider is responsible for loading the template contents. However, it's reasonable to use the {@link ResourceManager}, i.e. by means of
     * {@link Templates#loadTemplate(String, ResourceManager)}.
     *
     * @param templateName
     * @return the template for the given name
     */
    Template getTemplate(String templateName);

    /**
     * Allows the provider to perform any cleanup needed.
     *
     * @see UndertowJS#stop()
     */
    default void cleanup() {
    }

}
