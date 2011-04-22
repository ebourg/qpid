/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.test.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Qpid base class for system testing test cases.
 */
public class QpidBrokerTestCase extends QpidTestCase
{
    protected final String QpidHome = System.getProperty("QPID_HOME");
    protected File _configFile = new File(System.getProperty("broker.config"));

    protected static final Logger _logger = Logger.getLogger(QpidBrokerTestCase.class);
    protected static final int LOGMONITOR_TIMEOUT = 5000;

    protected long RECEIVE_TIMEOUT = 1000l;

    private Map<String, String> _propertiesSetForTestOnly = new HashMap<String, String>();
    private Map<String, String> _propertiesSetForBroker = new HashMap<String, String>();
    private Map<Logger, Level> _loggerLevelSetForTest = new HashMap<Logger, Level>();

    private XMLConfiguration _testConfiguration = new XMLConfiguration();
    private XMLConfiguration _testVirtualhosts = new XMLConfiguration();

    protected static final String INDEX = "index";
    protected static final String CONTENT = "content";

    private static final String DEFAULT_INITIAL_CONTEXT = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

    // system properties
    private static final String BROKER_LANGUAGE = "broker.language";
    private static final String BROKER = "broker";
    private static final String BROKER_CLEAN = "broker.clean";
    private static final String BROKER_CLEAN_BETWEEN_TESTS = "broker.clean.between.tests";
    private static final String BROKER_EXISTING_QPID_WORK = "broker.existing.qpid.work";
    private static final String BROKER_VERSION = "broker.version";
    protected static final String BROKER_READY = "broker.ready";
    private static final String BROKER_STOPPED = "broker.stopped";
    private static final String TEST_OUTPUT = "test.output";
    private static final String BROKER_LOG_INTERLEAVE = "broker.log.interleave";
    private static final String BROKER_LOG_PREFIX = "broker.log.prefix";
    private static final String BROKER_PERSITENT = "broker.persistent";

    // values
    protected static final String JAVA = "java";
    protected static final String CPP = "cpp";
    protected static final String VM = "vm";
    protected static final String EXTERNAL = "external";
    private static final String VERSION_010 = "0-10";

    protected static final String QPID_HOME = "QPID_HOME";

    public static final int DEFAULT_VM_PORT = 1;
    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_MANAGEMENT_PORT = 9099;

    protected String _brokerLanguage = System.getProperty(BROKER_LANGUAGE, JAVA);
    protected String _broker = System.getProperty(BROKER, VM);
    private String _brokerClean = System.getProperty(BROKER_CLEAN, null);
    private Boolean _brokerCleanBetweenTests = Boolean.getBoolean(BROKER_CLEAN_BETWEEN_TESTS);
    private String _brokerVersion = System.getProperty(BROKER_VERSION, VERSION_010);
    protected String _output = System.getProperty(TEST_OUTPUT);
    protected Boolean _brokerPersistent = Boolean.getBoolean(BROKER_PERSITENT);

    protected static String _brokerLogPrefix = System.getProperty(BROKER_LOG_PREFIX,"BROKER: ");
    protected static boolean _interleaveBrokerLog = Boolean.getBoolean(BROKER_LOG_INTERLEAVE);

    protected File _outputFile;

    protected PrintStream _brokerOutputStream;

    protected Map<Integer, Process> _brokers = new HashMap<Integer, Process>();

    protected String _testName;

    // the connections created for a given test
    public static final String QUEUE = "queue";
    public static final String TOPIC = "topic";
    
    /** Map to hold test defined environment properties */
    private Map<String, String> _env;

    /** Ensure our messages have some sort of size */
    protected static final int DEFAULT_MESSAGE_SIZE = 1024;
    
    /** Size to create our message*/
    private int _messageSize = DEFAULT_MESSAGE_SIZE;
    /** Type of message*/
    protected enum MessageType
    {
        BYTES,
        MAP,
        OBJECT,
        STREAM,
        TEXT
    }
    private MessageType _messageType  = MessageType.TEXT;

    public QpidBrokerTestCase(String name)
    {
        super(name);
    }
    
    public QpidBrokerTestCase()
    {
        super();
    }
    
	public Logger getLogger()
	{
		return QpidBrokerTestCase._logger;
	}

    public void runBare() throws Throwable
    {
        _testName = getClass().getSimpleName() + "." + getName();
        String qname = getClass().getName() + "." + getName();

        // Initialize this for each test run
        _env = new HashMap<String, String>();

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        PrintStream out = null;
        PrintStream err = null;

        boolean redirected = _output != null && _output.length() > 0;
        if (redirected)
        {
            _outputFile = new File(String.format("%s/TEST-%s.out", _output, qname));
            out = new PrintStream(_outputFile);
            err = new PrintStream(String.format("%s/TEST-%s.err", _output, qname));
            System.setOut(out);
            System.setErr(err);

            if (_interleaveBrokerLog)
            {
            	_brokerOutputStream = out;
            }
            else
            {
            	_brokerOutputStream = new PrintStream(new FileOutputStream(String
    					.format("%s/TEST-%s.broker.out", _output, qname)), true);
            }
        }

        _logger.info("========== start " + _testName + " ==========");
        try
        {
            super.runBare();
        }
        catch (Exception e)
        {
            _logger.error("exception", e);
            throw e;
        }
        finally
        {
            try
            {
                stopBroker();
            }
            catch (Exception e)
            {
                _logger.error("exception stopping broker", e);
            }

            if(_brokerCleanBetweenTests)
            {
            	try
            	{
            		cleanBroker();
            	}
            	catch (Exception e)
            	{
            		_logger.error("exception cleaning up broker", e);
            	}
            }

            _logger.info("==========  stop " + _testName + " ==========");

            if (redirected)
            {
                System.setErr(oldErr);
                System.setOut(oldOut);
                err.close();
                out.close();
                if (!_interleaveBrokerLog)
                {
                	_brokerOutputStream.close();
                }
            }
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        if (!_configFile.exists())
        {
            fail("Unable to test without config file:" + _configFile);
        }

        String existingQpidWorkPath = System.getProperty(BROKER_EXISTING_QPID_WORK);
        if(existingQpidWorkPath != null && !existingQpidWorkPath.equals(""))
        {
            cleanBroker();

            File existing = new File(existingQpidWorkPath);
            File qpidWork = new File(getQpidWork(_broker, getPort()));
        }

        startBroker();
    }

    private static final class Piper extends Thread
    {

        private LineNumberReader in;
        private PrintStream out;
        private String ready;
        private CountDownLatch latch;
        private boolean seenReady;
        private String stopped;
        private String stopLine;

        public Piper(InputStream in, PrintStream out, String ready)
        {
            this(in, out, ready, null);
        }

        public Piper(InputStream in, PrintStream out, String ready, String stopped)
        {
            this.in = new LineNumberReader(new InputStreamReader(in));
            this.out = out;
            this.ready = ready;
            this.stopped = stopped;
            this.seenReady = false;

            if (this.ready != null && !this.ready.equals(""))
            {
                this.latch = new CountDownLatch(1);
            }
            else
            {
                this.latch = null;
            }
        }

        public Piper(InputStream in, PrintStream out)
        {
            this(in, out, null);
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException
        {
            if (latch == null)
            {
                return true;
            }
            else
            {
                latch.await(timeout, unit);
                return seenReady;
            }
        }

        public void run()
        {
            try
            {
                String line;
                while ((line = in.readLine()) != null)
                {
                	if (_interleaveBrokerLog)
                	{
                		line = _brokerLogPrefix + line;
                	}
                	out.println(line);

                    if (latch != null && line.contains(ready))
                    {
                        seenReady = true;
                        latch.countDown();
                    }

                    if (!seenReady && line.contains(stopped))
                    {
                        stopLine = line;
                    }
                }
            }
            catch (IOException e)
            {
                // this seems to happen regularly even when
                // exits are normal
            }
            finally
            {
                if (latch != null)
                {
                    latch.countDown();
                }
            }
        }

        public String getStopLine()
        {
            return stopLine;
        }
    }

    public void startBroker() throws Exception
    {
        startBroker(0);
    }

    /**
     * Return the management portin use by the broker on this main port
     *
     * @param mainPort the broker's main port.
     *
     * @return the management port that corresponds to the broker on the given port
     */
    protected int getManagementPort(int mainPort)
    {
        return mainPort + (DEFAULT_MANAGEMENT_PORT - (_broker.equals(VM) ? DEFAULT_VM_PORT : DEFAULT_PORT));
    }

    /**
     * Get the Port that is use by the current broker
     *
     * @return the current port
     */
    protected int getPort()
    {
        return getPort(0);
    }

    protected int getPort(int port)
    {
        if (_broker.equals(VM))
        {
            return port == 0 ? DEFAULT_VM_PORT : port;
        }
        else if (!_broker.equals(EXTERNAL))
        {
            return port == 0 ? DEFAULT_PORT : port;
        }
        else
        {
            return port;
        }
    }

    protected String getBrokerCommand(int port) throws MalformedURLException
    {
        return _broker
                .replace("@PORT", "" + port)
                .replace("@SSL_PORT", "" + (port - 1))
                .replace("@MPORT", "" + getManagementPort(port))
                .replace("@CONFIG_FILE", _configFile.toString());
    }

    public void startBroker(int port) throws Exception
    {
        port = getPort(port);

        // Save any configuration changes that have been made
        saveTestConfiguration();
        saveTestVirtualhosts();

        Process process = null;
        if (!_broker.equals(EXTERNAL))
        {
            String cmd = getBrokerCommand(port);
            _logger.info("starting broker: " + cmd);
            ProcessBuilder pb = new ProcessBuilder(cmd.split("\\s+"));
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();

            String qpidHome = System.getProperty(QPID_HOME);
            env.put(QPID_HOME, qpidHome);

            //Augment Path with bin directory in QPID_HOME.
            env.put("PATH", env.get("PATH").concat(File.pathSeparator + qpidHome + "/bin"));

            //Add the test name to the broker run.
            // DON'T change PNAME, qpid.stop needs this value.
            env.put("QPID_PNAME", "-DPNAME=QPBRKR -DTNAME=\"" + _testName + "\"");
            // Add the port to QPID_WORK to ensure unique working dirs for multi broker tests
            env.put("QPID_WORK", getQpidWork(_broker, port));


            // Use the environment variable to set amqj.logging.level for the broker
            // The value used is a 'server' value in the test configuration to
            // allow a differentiation between the client and broker logging levels.
            if (System.getProperty("amqj.server.logging.level") != null)
            {
                setBrokerEnvironment("AMQJ_LOGGING_LEVEL", System.getProperty("amqj.server.logging.level"));
            }

            // Add all the environment settings the test requested
            if (!_env.isEmpty())
            {
                for (Map.Entry<String, String> entry : _env.entrySet())
                {
                    env.put(entry.getKey(), entry.getValue());
                }
            }


            // Add default test logging levels that are used by the log4j-test
            // Use the convenience methods to push the current logging setting
            // in to the external broker's QPID_OPTS string.
            if (System.getProperty("amqj.protocol.logging.level") != null)
            {
                setSystemProperty("amqj.protocol.logging.level");
            }
            if (System.getProperty("root.logging.level") != null)
            {
                setSystemProperty("root.logging.level");
            }


            String QPID_OPTS = " ";
            // Add all the specified system properties to QPID_OPTS
            if (!_propertiesSetForBroker.isEmpty())
            {
                for (String key : _propertiesSetForBroker.keySet())
                {
                    QPID_OPTS += "-D" + key + "=" + _propertiesSetForBroker.get(key) + " ";
                }

                if (env.containsKey("QPID_OPTS"))
                {
                    env.put("QPID_OPTS", env.get("QPID_OPTS") + QPID_OPTS);
                }
                else
                {
                    env.put("QPID_OPTS", QPID_OPTS);
                }
            }

            process = pb.start();

            Piper p = new Piper(process.getInputStream(),
            		            _brokerOutputStream,
                                System.getProperty(BROKER_READY),
                                System.getProperty(BROKER_STOPPED));

            p.start();

            if (!p.await(30, TimeUnit.SECONDS))
            {
                _logger.info("broker failed to become ready (" + p.ready + "):" + p.getStopLine());
                //Ensure broker has stopped
                process.destroy();
                cleanBroker();
                throw new RuntimeException("broker failed to become ready:"
                                           + p.getStopLine());
            }

            try
            {
                int exit = process.exitValue();
                _logger.info("broker aborted: " + exit);
                cleanBroker();
                throw new RuntimeException("broker aborted: " + exit);
            }
            catch (IllegalThreadStateException e)
            {
                // this is expect if the broker started succesfully
            }
        }

        _brokers.put(port, process);
    }

    private String getQpidWork(String broker, int port)
    {
        if (broker.equals(VM))
        {
            return System.getProperty("QPID_WORK");
        }
        else if (!broker.equals(EXTERNAL))
        {
            return System.getProperty("QPID_WORK")+ "/" + port;
        }

        return System.getProperty("QPID_WORK");
    }

    public String getTestConfigFile()
    {
        String path = _output == null ? System.getProperty("java.io.tmpdir") : _output;
        return path + "/" + getTestQueueName() + "-config.xml";
    }

    public String getTestVirtualhostsFile()
    {
        String path = _output == null ? System.getProperty("java.io.tmpdir") : _output;
        return path + "/" + getTestQueueName() + "-virtualhosts.xml";
    }

    protected void saveTestConfiguration() throws ConfigurationException
    {
        // Specifiy the test config file
        String testConfig = getTestConfigFile();
        setSystemProperty("test.config", testConfig);

        // Create the file if configuration does not exist
        if (_testConfiguration.isEmpty())
        {
            _testConfiguration.addProperty("__ignore", "true");
        }
        _testConfiguration.save(testConfig);
    }

    protected void saveTestVirtualhosts() throws ConfigurationException
    {
        // Specifiy the test virtualhosts file
        String testVirtualhosts = getTestVirtualhostsFile();
        setSystemProperty("test.virtualhosts", testVirtualhosts);

        // Create the file if configuration does not exist
        if (_testVirtualhosts.isEmpty())
        {
            _testVirtualhosts.addProperty("__ignore", "true");
        }
        _testVirtualhosts.save(testVirtualhosts);
    }

    public void cleanBroker()
    {
        if (_brokerClean != null)
        {
            _logger.info("clean: " + _brokerClean);

            try
            {
                ProcessBuilder pb = new ProcessBuilder(_brokerClean.split("\\s+"));
                pb.redirectErrorStream(true);
                Process clean = pb.start();
                new Piper(clean.getInputStream(),_brokerOutputStream).start();

                clean.waitFor();

                _logger.info("clean exited: " + clean.exitValue());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void stopBroker() throws Exception
    {
        stopBroker(0);
    }

    public void stopBroker(int port) throws Exception
    {
        port = getPort(port);

        _logger.info("stopping broker: " + getBrokerCommand(port));
        Process process = _brokers.remove(port);
        if (process != null)
        {
            process.destroy();
            process.waitFor();
            _logger.info("broker exited: " + process.exitValue());
        }
    }

    /**
     * Set a configuration Property for this test run.
     *
     * This creates a new configuration based on the current configuration
     * with the specified property change.
     *
     * Multiple calls to this method will result in multiple temporary
     * configuration files being created.
     *
     * @param property the configuration property to set
     * @param value    the new value
     *
     * @throws ConfigurationException when loading the current config file
     * @throws IOException            when writing the new config file
     */
    protected void setConfigurationProperty(String property, String value)
            throws ConfigurationException, IOException
    {
        // Choose which file to write the property to based on prefix.
        if (property.startsWith("virtualhosts"))
        {
            _testVirtualhosts.setProperty(StringUtils.substringAfter(property, "virtualhosts."), value);
        }
        else
        {
            _testConfiguration.setProperty(property, value);
        }
    }

    /**
     * Set a System property that is to be applied only to the external test
     * broker.
     *
     * This is a convenience method to enable the setting of a -Dproperty=value
     * entry in QPID_OPTS
     *
     * This is only useful for the External Java Broker tests.
     *
     * @param property the property name
     * @param value the value to set the property to
     */
    protected void setBrokerOnlySystemProperty(String property, String value)
    {
        if (!_propertiesSetForBroker.containsKey(property))
        {
            _propertiesSetForBroker.put(property, value);
        }

    }

    /**
     * Set a System (-D) property for this test run.
     *
     * This convenience method copies the current VMs System Property
     * for the external VM Broker.
     *
     * @param property the System property to set
     */
    protected void setSystemProperty(String property)
    {
        setSystemProperty(property, System.getProperty(property));
    }

    /**
     * Set a System property for the duration of this test.
     *
     * When the test run is complete the value will be reverted.
     *
     * The values set using this method will also be propogated to the external
     * Java Broker via a -D value defined in QPID_OPTS.
     *
     * If the value should not be set on the broker then use
     * setTestClientSystemProperty().
     *
     * @param property the property to set
     * @param value    the new value to use
     */
    protected void setSystemProperty(String property, String value)
    {
        // Record the value for the external broker
        _propertiesSetForBroker.put(property, value);

        //Set the value for the test client vm aswell.
        setTestClientSystemProperty(property, value);
    }

    /**
     * Set a System (-D) property for the external Broker of this test.
     *
     * @param property The property to set
     * @param value the value to set it to.
     */
    protected void setTestClientSystemProperty(String property, String value)
    {
        if (!_propertiesSetForTestOnly.containsKey(property))
        {
            // Record the current value so we can revert it later.
            _propertiesSetForTestOnly.put(property, System.getProperty(property));
        }

        System.setProperty(property, value);
    }

    /**
     * Restore the System property values that were set before this test run.
     */
    protected void revertSystemProperties()
    {
        for (String key : _propertiesSetForTestOnly.keySet())
        {
            String value = _propertiesSetForTestOnly.get(key);
            if (value != null)
            {
                System.setProperty(key, value);
            }
            else
            {
                System.clearProperty(key);
            }
        }

        _propertiesSetForTestOnly.clear();

        // We don't change the current VMs settings for Broker only properties
        // so we can just clear this map
        _propertiesSetForBroker.clear();
    }

    /**
     * Add an environtmen variable for the external broker environment
     *
     * @param property the property to set
     * @param value    the value to set it to
     */
    protected void setBrokerEnvironment(String property, String value)
    {
        _env.put(property, value);
    }

    /**
     * Adjust the VMs Log4j Settings just for this test run
     *
     * @param logger the logger to change
     * @param level the level to set
     */
    protected void setLoggerLevel(Logger logger, Level level)
    {
        assertNotNull("Cannot set level of null logger", logger);
        assertNotNull("Cannot set Logger("+logger.getName()+") to null level.",level);

        if (!_loggerLevelSetForTest.containsKey(logger))
        {
            // Record the current value so we can revert it later.
            _loggerLevelSetForTest.put(logger, logger.getLevel());
        }

        logger.setLevel(level);
    }

    /**
     * Restore the logging levels defined by this test.
     */
    protected void revertLoggingLevels()
    {
        for (Logger logger : _loggerLevelSetForTest.keySet())
        {
            logger.setLevel(_loggerLevelSetForTest.get(logger));
        }

        _loggerLevelSetForTest.clear();

    }

    /**
     * Check whether the broker is an 0.8
     *
     * @return true if the broker is an 0_8 version, false otherwise.
     */
    public boolean isBroker08()
    {
        return false;
    }

    public boolean isBroker010()
    {
        return _brokerVersion.equals(VERSION_010);
    }

    protected boolean isJavaBroker()
    {
        return _brokerLanguage.equals("java") || _broker.equals("vm");
    }

    protected boolean isCppBroker()
    {
        return _brokerLanguage.equals("cpp");
    }

    protected boolean isExternalBroker()
    {
        return !_broker.equals("vm");
    }
    
    protected boolean isBrokerStorePersistent()
    {
        return _brokerPersistent;
    }

    public void restartBroker() throws Exception
    {
        restartBroker(0);
    }

    public void restartBroker(int port) throws Exception
    {
        stopBroker(port);
        startBroker(port);
    }

    /**
     * Return a uniqueName for this test.
     * In this case it returns a queue Named by the TestCase and TestName
     *
     * @return String name for a queue
     */
    protected String getTestQueueName()
    {
        return getClass().getSimpleName() + "-" + getName();
    }


    protected void tearDown() throws java.lang.Exception
    {
        // Ensure any problems with close does not interfer with property resets
        revertSystemProperties();
        revertLoggingLevels();
    }

    protected int getMessageSize()
    {
        return _messageSize;
    }

    protected void setMessageSize(int byteSize)
    {
        _messageSize = byteSize;
    }

}
