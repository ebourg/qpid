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
package org.apache.qpid.client.handler;

import java.util.Map;
import java.util.HashMap;

import org.apache.qpid.framing.*;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.AMQMethodNotImplementedException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMethodDispatcherImpl implements MethodDispatcher
{
    private static final Logger _logger = LoggerFactory.getLogger(ClientMethodDispatcherImpl.class);

    private static interface DispatcherFactory
    {
        public ClientMethodDispatcherImpl createMethodDispatcher(AMQProtocolSession session);
    }

    private static final Map<ProtocolVersion, DispatcherFactory> _dispatcherFactories =
            new HashMap<ProtocolVersion, DispatcherFactory>();


    public static ClientMethodDispatcherImpl newMethodDispatcher(ProtocolVersion version, AMQProtocolSession session)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("New Method Dispatcher:" + session);
        }
        
        DispatcherFactory factory = _dispatcherFactories.get(version);
        return factory.createMethodDispatcher(session);
    }

}
