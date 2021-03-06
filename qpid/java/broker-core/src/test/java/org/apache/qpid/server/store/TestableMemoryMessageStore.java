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
package org.apache.qpid.server.store;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.queue.AMQQueue;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds some extra methods to the memory message store for testing purposes.
 */
public class TestableMemoryMessageStore extends TestMemoryMessageStore
{
    private final Map<Long, AMQQueue> _messages = new HashMap<Long, AMQQueue>();
    private final AtomicInteger _messageCount = new AtomicInteger(0);

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        return new TestableStoredMessage(super.addMessage(metaData));
    }

    public int getMessageCount()
    {
        return _messageCount.get();
    }

    public Map<Long, AMQQueue> getMessages()
    {
        return _messages;
    }

    private class TestableTransaction implements Transaction
    {
        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            getMessages().put(message.getMessageNumber(), (AMQQueue)queue);
        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message) throws AMQStoreException
        {
            getMessages().remove(message.getMessageNumber());
        }

        @Override
        public void commitTran() throws AMQStoreException
        {
        }

        @Override
        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        public void abortTran() throws AMQStoreException
        {
        }

        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
        }

        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
        }
    }


    @Override
    public Transaction newTransaction()
    {
        return new TestableTransaction();
    }


    private class TestableStoredMessage implements StoredMessage
    {
        private final StoredMessage _storedMessage;

        public TestableStoredMessage(StoredMessage storedMessage)
        {
            _messageCount.incrementAndGet();
            _storedMessage = storedMessage;
        }

        public StorableMessageMetaData getMetaData()
        {
            return _storedMessage.getMetaData();
        }

        public long getMessageNumber()
        {
            return _storedMessage.getMessageNumber();
        }

        public void addContent(int offsetInMessage, ByteBuffer src)
        {
            _storedMessage.addContent(offsetInMessage, src);
        }

        public int getContent(int offsetInMessage, ByteBuffer dst)
        {
            return _storedMessage.getContent(offsetInMessage, dst);
        }


        public ByteBuffer getContent(int offsetInMessage, int size)
        {
            return _storedMessage.getContent(offsetInMessage, size);
        }

        public StoreFuture flushToStore()
        {
            return _storedMessage.flushToStore();
        }

        public void remove()
        {
            _storedMessage.remove();
            _messageCount.decrementAndGet();
        }
    }
}
