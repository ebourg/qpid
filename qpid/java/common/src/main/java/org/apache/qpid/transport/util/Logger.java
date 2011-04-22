/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.transport.util;

import java.util.logging.Level;

/**
 * Logger
 *
 */

public final class Logger
{

    public static final Logger get(Class<?> klass)
    {
        return new Logger(java.util.logging.Logger.getLogger(klass.getName()));
    }

    private final java.util.logging.Logger log;

    private Logger(java.util.logging.Logger log)
    {
        this.log = log;
    }

    public boolean isDebugEnabled()
    {
        return log.isLoggable(Level.FINE);
    }

    public void debug(String message, Object ... args)
    {
        if (log.isLoggable(Level.FINE))
        {
            log.fine(String.format(message, args));
        }
    }

    public void debug(Throwable t, String message, Object ... args)
    {
        if (log.isLoggable(Level.FINE))
        {
            log.log(Level.FINE, String.format(message, args), t);
        }
    }

    public void error(String message, Object ... args)
    {
        if (log.isLoggable(Level.SEVERE))
        {
            log.severe(String.format(message, args));
        }
    }

    public void error(Throwable t, String message, Object ... args)
    {
        if (log.isLoggable(Level.SEVERE))
        {
            log.log(Level.SEVERE, String.format(message, args), t);
        }
    }

    public void warn(String message, Object ... args)
    {
        if (log.isLoggable(Level.WARNING))
        {
            log.warning(String.format(message, args));
        }
    }

    public void warn(Throwable t, String message, Object ... args)
    {
        if (log.isLoggable(Level.WARNING))
        {
            log.log(Level.WARNING, String.format(message, args), t);
        }
    }

    public void info(String message, Object ... args)
    {
        if (log.isLoggable(Level.INFO))
        {
            log.info(String.format(message, args));
        }
    }

    public void info(Throwable t, String message, Object ... args)
    {
        if (log.isLoggable(Level.INFO))
        {
            log.log(Level.INFO, String.format(message, args), t);
        }
    }

    public void trace(String message, Object ... args)
    {
        if (log.isLoggable(Level.FINEST))
        {
            log.finest(String.format(message, args));
        }
    }

    public void trace(Throwable t, String message, Object ... args)
    {
        if (log.isLoggable(Level.FINEST))
        {
            log.log(Level.FINEST, String.format(message, args), t);
        }
    }

}
