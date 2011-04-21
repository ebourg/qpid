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

import javax.jms.JMSException;

public class TestMessageHelper
{
    public static JMSTextMessage newJMSTextMessage() throws JMSException
    {
        return new JMSTextMessage(AMQMessageDelegateFactory.FACTORY_0_10);
    }

    public static JMSBytesMessage newJMSBytesMessage() throws JMSException
    {
        return new JMSBytesMessage(AMQMessageDelegateFactory.FACTORY_0_10);
    }

    public static JMSMapMessage newJMSMapMessage() throws JMSException
    {
        return new JMSMapMessage(AMQMessageDelegateFactory.FACTORY_0_10);
    }

    public static JMSStreamMessage newJMSStreamMessage()
    {
        return new JMSStreamMessage(AMQMessageDelegateFactory.FACTORY_0_10);
    }
}
