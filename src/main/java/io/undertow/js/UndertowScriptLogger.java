/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.js;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * log messages start at 40000
 *
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "UTJS")
public interface UndertowScriptLogger extends BasicLogger {

    UndertowScriptLogger ROOT_LOGGER = Logger.getMessageLogger(UndertowScriptLogger.class, UndertowScriptLogger.class.getPackage().getName());

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 1, value = "Failed to rebuild script engine after file change")
    void failedToRebuildScriptEngine(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 2, value = "Could not find script file %s")
    void couldNotReadResource(String resource);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 3, value = "Rebuilding Javascript ending as %s has changed")
    void rebuildingDueToFileChange(String resource);

    @Message(id = 4, value = "Could not find script file %s")
    RuntimeException couldNotFileScript(String path);

    @Message(id = 5, value = "Could not find template file %s")
    IllegalArgumentException templateNotFound(String template);
}
