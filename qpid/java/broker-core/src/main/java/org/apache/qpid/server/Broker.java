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
package org.apache.qpid.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.BrokerConfigurationStoreCreator;
import org.apache.qpid.server.configuration.store.ManagementModeStoreHandler;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;

public class Broker
{
    private static final Logger LOGGER = Logger.getLogger(Broker.class);

    private volatile Thread _shutdownHookThread;
    private volatile IApplicationRegistry _applicationRegistry;

    protected static class InitException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        InitException(String msg, Throwable cause)
        {
            super(msg, cause);
        }
    }

    public void shutdown()
    {
        try
        {
            removeShutdownHook();
        }
        finally
        {
            if (_applicationRegistry != null)
            {
                _applicationRegistry.close();
            }

        }
    }

    public void startup() throws Exception
    {
        startup(new BrokerOptions());
    }

    public void startup(final BrokerOptions options) throws Exception
    {
        CurrentActor.set(new BrokerActor(new SystemOutMessageLogger()));
        try
        {
            startupImpl(options);
            addShutdownHook();
        }
        finally
        {
            CurrentActor.remove();

        }
    }

    private void startupImpl(final BrokerOptions options) throws Exception
    {
        String storeLocation = options.getConfigurationStoreLocation();
        String storeType = options.getConfigurationStoreType();

        CurrentActor.get().message(BrokerMessages.CONFIG(storeLocation));

        //Allow skipping the logging configuration for people who are
        //embedding the broker and want to configure it themselves.
        if(!options.isSkipLoggingConfiguration())
        {
            configureLogging(new File(options.getLogConfigFileLocation()), options.getLogWatchFrequency());
        }

        BrokerConfigurationStoreCreator storeCreator = new BrokerConfigurationStoreCreator();
        ConfigurationEntryStore store = storeCreator.createStore(storeLocation, storeType, options.getInitialConfigurationLocation(),
                                                                 options.isOverwriteConfigurationStore(), options.getConfigProperties());

        if (options.isManagementMode())
        {
            store = new ManagementModeStoreHandler(store, options);
        }

        _applicationRegistry = new ApplicationRegistry(store);
        try
        {
            _applicationRegistry.initialise(options);
        }
        catch(Exception e)
        {
            LOGGER.fatal("Exception during startup", e);
            try
            {
                _applicationRegistry.close();
            }
            catch(Exception ce)
            {
                LOGGER.debug("An error occurred when closing the registry following initialization failure", ce);
            }
            throw e;
        }

    }

    public static void parsePortList(Set<Integer> output, List<?> ports) throws InitException
    {
        if(ports != null)
        {
            for(Object o : ports)
            {
                try
                {
                    output.add(Integer.parseInt(String.valueOf(o)));
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port: " + o, e);
                }
            }
        }
    }

    private void configureLogging(File logConfigFile, int logWatchTime) throws InitException, IOException
    {
        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            CurrentActor.get().message(BrokerMessages.LOG_CONFIG(logConfigFile.getAbsolutePath()));

            if (logWatchTime > 0)
            {
                System.out.println("log file " + logConfigFile.getAbsolutePath() + " will be checked for changes every "
                        + logWatchTime + " seconds");
                // log4j expects the watch interval in milliseconds
                try
                {
                    LoggingManagementFacade.configureAndWatch(logConfigFile.getPath(), logWatchTime * 1000);
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
            else
            {
                try
                {
                    LoggingManagementFacade.configure(logConfigFile.getPath());
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
        }
        else
        {
            System.err.println("Logging configuration error: unable to read file " + logConfigFile.getAbsolutePath());
            System.err.println("Using the fallback internal fallback-log4j.properties configuration");

            InputStream propsFile = this.getClass().getResourceAsStream("/fallback-log4j.properties");
            if(propsFile == null)
            {
                throw new IOException("Unable to load the fallback internal fallback-log4j.properties configuration file");
            }
            else
            {
                try
                {
                    Properties fallbackProps = new Properties();
                    fallbackProps.load(propsFile);
                    PropertyConfigurator.configure(fallbackProps);
                }
                finally
                {
                    propsFile.close();
                }
            }
        }
    }


    private void addShutdownHook()
    {
        Thread shutdownHookThread = new Thread(new ShutdownService());
        shutdownHookThread.setName("QpidBrokerShutdownHook");

        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        _shutdownHookThread = shutdownHookThread;

        LOGGER.debug("Added shutdown hook");
    }

    private void removeShutdownHook()
    {
        Thread shutdownThread = _shutdownHookThread;

        //if there is a shutdown thread and we aren't it, we should remove it
        if(shutdownThread != null && !(Thread.currentThread() == shutdownThread))
        {
            LOGGER.debug("Removing shutdown hook");

            _shutdownHookThread = null;

            boolean removed = false;
            try
            {
                removed = Runtime.getRuntime().removeShutdownHook(shutdownThread);
            }
            catch(IllegalStateException ise)
            {
                //ignore, means the JVM is already shutting down
            }

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Removed shutdown hook: " + removed);
            }
        }
        else
        {
            LOGGER.debug("Skipping shutdown hook removal as there either isn't one, or we are it.");
        }
    }

    public org.apache.qpid.server.model.Broker getBroker()
    {
        if (_applicationRegistry == null)
        {
            return null;
        }
        return _applicationRegistry.getBroker();
    }

    private class ShutdownService implements Runnable
    {
        public void run()
        {
            LOGGER.debug("Shutdown hook running");
            Broker.this.shutdown();
        }
    }

}
