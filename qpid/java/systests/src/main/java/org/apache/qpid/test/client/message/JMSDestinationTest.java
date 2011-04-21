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
package org.apache.qpid.test.client.message;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularData;
import java.nio.BufferOverflowException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * From the API Docs getJMSDestination:
 *
 * When a message is received, its JMSDestination value must be equivalent to
 * the value assigned when it was sent.
 */
public class JMSDestinationTest extends QpidBrokerTestCase
{

    private Connection _connection;
    private Session _session;

    private static final String USER = "admin";
    private CountDownLatch _receiveMessage;
    private Message _message;

    public void setUp() throws Exception
    {
        //Ensure JMX management is enabled for MovedToQueue test 
        setConfigurationProperty("management.enabled", "true");

        super.setUp();

        _connection = getConnection();

        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Test a message sent to a queue comes back with JMSDestination queue
     *
     * @throws Exception
     */
    public void testQueue() throws Exception
    {

        Queue queue = _session.createQueue(getTestQueueName());

        MessageConsumer consumer = _session.createConsumer(queue);

        sendMessage(_session, queue, 1);

        _connection.start();

        Message message = consumer.receive(10000);

        assertNotNull("Message should not be null", message);

        Destination destination = message.getJMSDestination();

        assertNotNull("JMSDestination should not be null", destination);

        assertEquals("Incorrect Destination type", queue.getClass(), destination.getClass());
    }

    /**
     * Test a message sent to a topic comes back with JMSDestination topic
     *
     * @throws Exception
     */
    public void testTopic() throws Exception
    {

        Topic topic = _session.createTopic(getTestQueueName() + "Topic");

        MessageConsumer consumer = _session.createConsumer(topic);

        sendMessage(_session, topic, 1);

        _connection.start();

        Message message = consumer.receive(10000);

        assertNotNull("Message should not be null", message);

        Destination destination = message.getJMSDestination();

        assertNotNull("JMSDestination should not be null", destination);

        assertEquals("Incorrect Destination type", topic.getClass(), destination.getClass());
    }

    /**
     * Test a message sent to a queue comes back with JMSDestination queue
     * when received via a message listener
     *
     * @throws Exception
     */
    public void testQueueAsync() throws Exception
    {

        Queue queue = _session.createQueue(getTestQueueName());

        MessageConsumer consumer = _session.createConsumer(queue);

        sendMessage(_session, queue, 1);

        _connection.start();

        _message = null;
        _receiveMessage = new CountDownLatch(1);

        consumer.setMessageListener(new MessageListener()
        {
            public void onMessage(Message message)
            {
                _message = message;
                _receiveMessage.countDown();
            }
        });

        assertTrue("Timed out waiting for message to be received ", _receiveMessage.await(1, TimeUnit.SECONDS));

        assertNotNull("Message should not be null", _message);

        Destination destination = _message.getJMSDestination();

        assertNotNull("JMSDestination should not be null", destination);

        assertEquals("Incorrect Destination type", queue.getClass(), destination.getClass());
    }

    /**
     * Test a message received without the JMS_QPID_DESTTYPE can be resent
     * and correctly have the property set.
     *
     * To do this we need to create a 0-10 connection and send a message
     * which is then received by a 0-8/9 client.
     *
     * @throws Exception
     */
    public void testReceiveResend() throws Exception
    {
        // Create a 0-10 Connection and send message
        setSystemProperty(ClientProperties.AMQP_VERSION, "0-10");

        Connection connection010 = getConnection();

        Session session010 = connection010.createSession(true, Session.SESSION_TRANSACTED);

        // Create queue for testing
        Queue queue = session010.createQueue(getTestQueueName());

        // Ensure queue exists
        session010.createConsumer(queue).close();

        sendMessage(session010, queue, 1);

        // Close the 010 connection
        connection010.close();

        // Create a 0-8 Connection and receive message
        setSystemProperty(ClientProperties.AMQP_VERSION, "0-8");

        Connection connection08 = getConnection();

        Session session08 = connection08.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageConsumer consumer = session08.createConsumer(queue);

        connection08.start();

        Message message = consumer.receive(1000);

        assertNotNull("Didn't receive 0-10 message.", message);

        // Validate that JMS_QPID_DESTTYPE is not set
        try
        {
            message.getIntProperty(CustomJMSXProperty.JMS_QPID_DESTTYPE.toString());
            fail("JMS_QPID_DESTTYPE should not be set, so should throw NumberFormatException");
        }
        catch (NumberFormatException nfe)
        {

        }

        // Resend message back to queue and validate that
        // a) getJMSDestination works without the JMS_QPID_DESTTYPE
        // b) we can actually send without a BufferOverFlow.

        MessageProducer producer = session08.createProducer(queue);

        try
        {
            producer.send(message);
        }
        catch (BufferOverflowException bofe)
        {
            // Print the stack trace so we can validate where the execption occured.
            bofe.printStackTrace();
            fail("BufferOverflowException thrown during send");
        }

        message = consumer.receive(1000);

        assertNotNull("Didn't receive recent 0-8 message.", message);

        // Validate that JMS_QPID_DESTTYPE is not set
        assertEquals("JMS_QPID_DESTTYPE should be set to a Queue", AMQDestination.QUEUE_TYPE,
                     message.getIntProperty(CustomJMSXProperty.JMS_QPID_DESTTYPE.toString()));

    }

    /**
     * Send a message to a custom exchange and then verify
     * the message received has the proper destination set
     *
     * @throws Exception
     */
    public void testGetDestinationWithCustomExchange() throws Exception
    {

        AMQDestination dest = new AMQAnyDestination(new AMQShortString("my-exchange"),
                                                    new AMQShortString("direct"),
                                                    new AMQShortString("test"),
                                                    false,
                                                    false,
                                                    new AMQShortString("test"),
                                                    false,
                                                    new AMQShortString[]{new AMQShortString("test")});
        
        // to force the creation of my-exchange.
        sendMessage(_session, dest, 1);
        
        MessageProducer prod = _session.createProducer(dest);
        
        MessageConsumer consumer = _session.createConsumer(dest);
        
        _connection.start();

        sendMessage(_session, dest, 1);
        
        Message message = consumer.receive(10000);

        assertNotNull("Message should not be null", message);

        Destination destination = message.getJMSDestination();

        assertNotNull("JMSDestination should not be null", destination);

        assertEquals("Incorrect Destination name", "my-exchange", dest.getExchangeName().asString());
        assertEquals("Incorrect Destination type", "direct", dest.getExchangeClass().asString());
        assertEquals("Incorrect Routing Key", "test", dest.getRoutingKey().asString());
    }
}
