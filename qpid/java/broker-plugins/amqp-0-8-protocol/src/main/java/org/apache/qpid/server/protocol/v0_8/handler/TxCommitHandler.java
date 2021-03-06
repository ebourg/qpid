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
package org.apache.qpid.server.protocol.v0_8.handler;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.TxCommitBody;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;

public class TxCommitHandler implements StateAwareMethodListener<TxCommitBody>
{
    private static final Logger _log = Logger.getLogger(TxCommitHandler.class);

    private static TxCommitHandler _instance = new TxCommitHandler();

    public static TxCommitHandler getInstance()
    {
        return _instance;
    }

    private TxCommitHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, TxCommitBody body, final int channelId) throws AMQException
    {
        final AMQProtocolSession session = stateManager.getProtocolSession();

        try
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Commit received on channel " + channelId);
            }
            AMQChannel channel = session.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId);
            }
            channel.commit(new Runnable()
            {

                @Override
                public void run()
                {
                    MethodRegistry methodRegistry = session.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createTxCommitOkBody();
                    session.writeFrame(responseBody.generateFrame(channelId));
                }
            }, true);



        }
        catch (AMQException e)
        {
            throw body.getChannelException(e.getErrorCode(), "Failed to commit: " + e.getMessage());
        }
    }
}
