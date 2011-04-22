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
package org.apache.qpid.client.transport;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.ExistingSocketConnector;
import org.apache.mina.transport.socket.nio.MultiThreadSocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.thread.QpidThreadExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TransportConnection is a helper class responsible for connecting to an AMQ server. It sets up the underlying
 * connector, which currently always uses TCP/IP sockets. It creates the "protocol handler" which deals with MINA
 * protocol events. <p/> Could be extended in future to support different transport types by turning this into concrete
 * class/interface combo.
 */
public class TransportConnection
{
    private static final int TCP = 0;
    private static final int SOCKET = 2;

    private static Logger _logger = LoggerFactory.getLogger(TransportConnection.class);

    private static final String DEFAULT_QPID_SERVER = "org.apache.qpid.server.protocol.AMQProtocolEngineFactory";

    private static Map<String, Socket> _openSocketRegister = new ConcurrentHashMap<String, Socket>();

    public static void registerOpenSocket(String socketID, Socket openSocket)
    {
        _openSocketRegister.put(socketID, openSocket);
    }

    public static Socket removeOpenSocket(String socketID)
    {
        return _openSocketRegister.remove(socketID);
    }

    public static synchronized ITransportConnection getInstance(final BrokerDetails details) throws AMQTransportConnectionException
    {
        int transport = getTransport(details.getTransport());

        if (transport == -1)
        {
            throw new AMQNoTransportForProtocolException(details, null, null);
        }

        switch (transport)
        {
            case SOCKET:
                return new SocketTransportConnection(new SocketTransportConnection.SocketConnectorFactory()
                {
                    public IoConnector newSocketConnector()
                    {
                        ExistingSocketConnector connector = new ExistingSocketConnector(1,new QpidThreadExecutor());

                        Socket socket = TransportConnection.removeOpenSocket(details.getHost());

                        if (socket != null)
                        {
                            _logger.info("Using existing Socket:" + socket);

                            ((ExistingSocketConnector) connector).setOpenSocket(socket);
                        }
                        else
                        {
                            throw new IllegalArgumentException("Active Socket must be provided for broker " +
                                                               "with 'socket://<SocketID>' transport:" + details);
                        }
                        return connector;
                    }
                });
            case TCP:
                return new SocketTransportConnection(new SocketTransportConnection.SocketConnectorFactory()
                {
                    public IoConnector newSocketConnector()
                    {
                        SocketConnector result;
                        // FIXME - this needs to be sorted to use the new Mina MultiThread SA.
                        if (Boolean.getBoolean("qpidnio"))
                        {
                            _logger.warn("Using Qpid MultiThreaded NIO - " + (System.getProperties().containsKey("qpidnio")
                                                                              ? "Qpid NIO is new default"
                                                                              : "Sysproperty 'qpidnio' is set"));
                            result = new MultiThreadSocketConnector(1, new QpidThreadExecutor());
                        }
                        else
                        {
                            _logger.info("Using Mina NIO");
                            result = new SocketConnector(1, new QpidThreadExecutor()); // non-blocking connector
                        }
                        // Don't have the connector's worker thread wait around for other connections (we only use
                        // one SocketConnector per connection at the moment anyway). This allows short-running
                        // clients (like unit tests) to complete quickly.
                        result.setWorkerTimeout(0);
                        return result;
                    }
                });
            default:
                throw new AMQNoTransportForProtocolException(details, "Transport not recognised:" + transport, null);
        }
    }

    private static int getTransport(String transport)
    {
        if (transport.equals(BrokerDetails.SOCKET))
        {
            return SOCKET;
        }

        if (transport.equals(BrokerDetails.TCP))
        {
            return TCP;
        }

        return -1;
    }

}
