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
package org.apache.qpid.client.message;

import org.apache.mina.common.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.DeliveryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;

public abstract class AbstractJMSMessageFactory implements MessageFactory
{
    private static final Logger _logger = LoggerFactory.getLogger(AbstractJMSMessageFactory.class);

    protected abstract AbstractJMSMessage createMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException;


    protected AbstractJMSMessage create010MessageWithBody(long messageNbr, MessageProperties msgProps,
                                                          DeliveryProperties deliveryProps,  
                                                          java.nio.ByteBuffer body) throws AMQException
    {
        ByteBuffer data;
        final boolean debug = _logger.isDebugEnabled();


        if (body != null)
        {
            data = ByteBuffer.wrap(body);
        }
        else // body == null
        {
            data = ByteBuffer.allocate(0);
        }

        if (debug)
        {
            _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data
                    .remaining());
        }
        AMQMessageDelegate delegate = new AMQMessageDelegate_0_10(msgProps, deliveryProps, messageNbr);

        AbstractJMSMessage message = createMessage(delegate, data);
        return message;
    }


    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered, MessageProperties msgProps,
                                            DeliveryProperties deliveryProps, java.nio.ByteBuffer body)
            throws JMSException, AMQException
    {
        final AbstractJMSMessage msg =
                create010MessageWithBody(messageNbr,msgProps,deliveryProps, body);
        msg.setJMSRedelivered(redelivered);
        msg.receivedFromServer();
        return msg;
    }

}
