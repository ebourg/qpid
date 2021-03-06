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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler.StoredMessageRecoveryHandler;
import org.apache.qpid.server.store.Transaction.Record;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public abstract class AbstractDurableConfigurationStoreTestCase extends QpidTestCase
{
    private static final String EXCHANGE_NAME = "exchangeName";

    private static final String EXCHANGE = org.apache.qpid.server.model.Exchange.class.getSimpleName();
    private static final String BINDING = org.apache.qpid.server.model.Binding.class.getSimpleName();
    private static final String QUEUE = Queue.class.getSimpleName();

    private String _storePath;
    private String _storeName;
    private MessageStore _messageStore;
    private Configuration _configuration;
    private VirtualHost _virtualHost;

    private ConfigurationRecoveryHandler _recoveryHandler;
    private MessageStoreRecoveryHandler _messageStoreRecoveryHandler;
    private StoredMessageRecoveryHandler _storedMessageRecoveryHandler;
    private TransactionLogRecoveryHandler _logRecoveryHandler;
    private TransactionLogRecoveryHandler.QueueEntryRecoveryHandler _queueEntryRecoveryHandler;
    private TransactionLogRecoveryHandler.DtxRecordRecoveryHandler _dtxRecordRecoveryHandler;

    private Exchange _exchange = mock(Exchange.class);
    private static final String ROUTING_KEY = "routingKey";
    private static final String QUEUE_NAME = "queueName";
    private Map<String,Object> _bindingArgs;
    private UUID _queueId;
    private UUID _exchangeId;
    private DurableConfigurationStore _configStore;

    public void setUp() throws Exception
    {
        super.setUp();

        _queueId = UUIDGenerator.generateRandomUUID();
        _exchangeId = UUIDGenerator.generateRandomUUID();

        _storeName = getName();
        _storePath = TMP_FOLDER + File.separator + _storeName;
        FileUtils.delete(new File(_storePath), true);
        setTestSystemProperty("QPID_WORK", TMP_FOLDER);
        _configuration = mock(Configuration.class);
        _recoveryHandler = mock(ConfigurationRecoveryHandler.class);
        _storedMessageRecoveryHandler = mock(StoredMessageRecoveryHandler.class);
        _logRecoveryHandler = mock(TransactionLogRecoveryHandler.class);
        _messageStoreRecoveryHandler = mock(MessageStoreRecoveryHandler.class);
        _queueEntryRecoveryHandler = mock(TransactionLogRecoveryHandler.QueueEntryRecoveryHandler.class);
        _dtxRecordRecoveryHandler = mock(TransactionLogRecoveryHandler.DtxRecordRecoveryHandler.class);
        _virtualHost = mock(VirtualHost.class);

        when(_messageStoreRecoveryHandler.begin()).thenReturn(_storedMessageRecoveryHandler);
        when(_logRecoveryHandler.begin(any(MessageStore.class))).thenReturn(_queueEntryRecoveryHandler);
        when(_queueEntryRecoveryHandler.completeQueueEntryRecovery()).thenReturn(_dtxRecordRecoveryHandler);
        when(_exchange.getName()).thenReturn(EXCHANGE_NAME);

        when(_exchange.getId()).thenReturn(_exchangeId);
        when(_configuration.getString(eq(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY), anyString())).thenReturn(
                _storePath);
        when(_virtualHost.getAttribute(eq(VirtualHost.STORE_PATH))).thenReturn(_storePath);

        _bindingArgs = new HashMap<String, Object>();
        String argKey = AMQPFilterTypes.JMS_SELECTOR.toString();
        String argValue = "some selector expression";
        _bindingArgs.put(argKey, argValue);

        reopenStore();
    }

    public void tearDown() throws Exception
    {
        try
        {
            closeMessageStore();
            closeConfigStore();
            FileUtils.delete(new File(_storePath), true);
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testCreateExchange() throws Exception
    {
        Exchange exchange = createTestExchange();
        DurableConfigurationStoreHelper.createExchange(_configStore, exchange);

        reopenStore();
        verify(_recoveryHandler).configuredObject(eq(_exchangeId), eq(EXCHANGE),
                eq(map( org.apache.qpid.server.model.Exchange.NAME, getName(),
                        org.apache.qpid.server.model.Exchange.TYPE, getName()+"Type",
                        org.apache.qpid.server.model.Exchange.LIFETIME_POLICY, LifetimePolicy.AUTO_DELETE.toString())));
    }

    private Map<String,Object> map(Object... vals)
    {
        Map<String,Object> map = new HashMap<String, Object>();
        boolean isValue = false;
        String key = null;
        for(Object obj : vals)
        {
            if(isValue)
            {
                map.put(key,obj);
            }
            else
            {
                key = (String) obj;
            }
            isValue = !isValue;
        }
        return map;
    }

    public void testRemoveExchange() throws Exception
    {
        Exchange exchange = createTestExchange();
        DurableConfigurationStoreHelper.createExchange(_configStore, exchange);

        DurableConfigurationStoreHelper.removeExchange(_configStore, exchange);

        reopenStore();
        verify(_recoveryHandler, never()).configuredObject(any(UUID.class), anyString(), anyMap());
    }

    public void testBindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false, null);
        Binding binding = new Binding(UUIDGenerator.generateRandomUUID(), ROUTING_KEY, queue,
                _exchange, _bindingArgs);
        DurableConfigurationStoreHelper.createBinding(_configStore, binding);

        reopenStore();

        Map<String,Object> map = new HashMap<String, Object>();
        map.put(org.apache.qpid.server.model.Binding.EXCHANGE, _exchange.getId().toString());
        map.put(org.apache.qpid.server.model.Binding.QUEUE, queue.getId().toString());
        map.put(org.apache.qpid.server.model.Binding.NAME, ROUTING_KEY);
        map.put(org.apache.qpid.server.model.Binding.ARGUMENTS,_bindingArgs);

        verify(_recoveryHandler).configuredObject(eq(binding.getId()), eq(BINDING),
                eq(map));
    }

    public void testUnbindQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(QUEUE_NAME, "queueOwner", false, null);
        Binding binding = new Binding(UUIDGenerator.generateRandomUUID(), ROUTING_KEY, queue,
                _exchange, _bindingArgs);
        DurableConfigurationStoreHelper.createBinding(_configStore, binding);

        DurableConfigurationStoreHelper.removeBinding(_configStore, binding);
        reopenStore();

        verify(_recoveryHandler, never()).configuredObject(any(UUID.class),
                eq(BINDING),
                anyMap());
    }

    public void testCreateQueueAMQQueue() throws Exception
    {
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, null);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();
        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, Boolean.TRUE);
        verify(_recoveryHandler).configuredObject(eq(_queueId), eq(QUEUE), eq(queueAttributes));
    }

    public void testCreateQueueAMQQueueFieldTable() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);

        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();


        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, Boolean.TRUE);
        queueAttributes.putAll(attributes);

        verify(_recoveryHandler).configuredObject(eq(_queueId), eq(QUEUE), eq(queueAttributes));
    }

    public void testCreateQueueAMQQueueWithAlternateExchange() throws Exception
    {
        Exchange alternateExchange = createTestAlternateExchange();

        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, alternateExchange, null);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        reopenStore();

        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, Boolean.TRUE);
        queueAttributes.put(Queue.ALTERNATE_EXCHANGE, alternateExchange.getId().toString());

        verify(_recoveryHandler).configuredObject(eq(_queueId), eq(QUEUE), eq(queueAttributes));
    }

    private Exchange createTestAlternateExchange()
    {
        UUID exchUuid = UUID.randomUUID();
        Exchange alternateExchange = mock(Exchange.class);
        when(alternateExchange.getId()).thenReturn(exchUuid);
        return alternateExchange;
    }

    public void testUpdateQueueExclusivity() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);

        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // update the queue to have exclusive=false
        queue = createTestQueue(getName(), getName() + "Owner", false, attributes);

        DurableConfigurationStoreHelper.updateQueue(_configStore, queue);

        reopenStore();

        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, Boolean.FALSE);
        queueAttributes.putAll(attributes);

        verify(_recoveryHandler).configuredObject(eq(_queueId), eq(QUEUE), eq(queueAttributes));

    }

    public void testUpdateQueueAlternateExchange() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // update the queue to have exclusive=false
        Exchange alternateExchange = createTestAlternateExchange();
        queue = createTestQueue(getName(), getName() + "Owner", false, alternateExchange, attributes);

        DurableConfigurationStoreHelper.updateQueue(_configStore, queue);

        reopenStore();

        Map<String,Object> queueAttributes = new HashMap<String, Object>();

        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(Queue.OWNER, getName()+"Owner");
        queueAttributes.put(Queue.EXCLUSIVE, Boolean.FALSE);
        queueAttributes.putAll(attributes);
        queueAttributes.put(Queue.ALTERNATE_EXCHANGE, alternateExchange.getId().toString());

        verify(_recoveryHandler).configuredObject(eq(_queueId), eq(QUEUE), eq(queueAttributes));
    }

    public void testRemoveQueue() throws Exception
    {
        // create queue
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, Boolean.TRUE);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 10);
        AMQQueue queue = createTestQueue(getName(), getName() + "Owner", true, attributes);
        DurableConfigurationStoreHelper.createQueue(_configStore, queue);

        // remove queue
        DurableConfigurationStoreHelper.removeQueue(_configStore,queue);
        reopenStore();
        verify(_recoveryHandler, never()).configuredObject(any(UUID.class),
                eq(org.apache.qpid.server.model.Queue.class.getName()),
                anyMap());
    }

    private AMQQueue createTestQueue(String queueName,
                                     String queueOwner,
                                     boolean exclusive,
                                     final Map<String, Object> arguments) throws AMQStoreException
    {
        return createTestQueue(queueName, queueOwner, exclusive, null, arguments);
    }

    private AMQQueue createTestQueue(String queueName,
                                     String queueOwner,
                                     boolean exclusive,
                                     Exchange alternateExchange,
                                     final Map<String, Object> arguments) throws AMQStoreException
    {
        AMQQueue queue = mock(AMQQueue.class);
        when(queue.getName()).thenReturn(queueName);
        when(queue.getOwner()).thenReturn(queueOwner);
        when(queue.isExclusive()).thenReturn(exclusive);
        when(queue.getId()).thenReturn(_queueId);
        when(queue.getAlternateExchange()).thenReturn(alternateExchange);
        if(arguments != null && !arguments.isEmpty())
        {
            when(queue.getAvailableAttributes()).thenReturn(arguments.keySet());
            final ArgumentCaptor<String> requestedAttribute = ArgumentCaptor.forClass(String.class);
            when(queue.getAttribute(requestedAttribute.capture())).then(
                    new Answer()
                    {

                        @Override
                        public Object answer(final InvocationOnMock invocation) throws Throwable
                        {
                            String attrName = requestedAttribute.getValue();
                            return arguments.get(attrName);
                        }
                    });
        }

        return queue;
    }

    private Exchange createTestExchange()
    {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getName()).thenReturn(getName());
        when(exchange.getTypeName()).thenReturn(getName() + "Type");
        when(exchange.isAutoDelete()).thenReturn(true);
        when(exchange.getId()).thenReturn(_exchangeId);
        return exchange;
    }

    private void reopenStore() throws Exception
    {
        closeMessageStore();
        closeConfigStore();
        _messageStore = createMessageStore();
        _configStore = createConfigStore();

        _configStore.configureConfigStore(_virtualHost, _recoveryHandler);
        _messageStore.configureMessageStore(_virtualHost, _messageStoreRecoveryHandler, _logRecoveryHandler);
        _messageStore.activate();
    }

    protected abstract MessageStore createMessageStore() throws Exception;
    protected abstract DurableConfigurationStore createConfigStore() throws Exception;
    protected abstract void closeMessageStore() throws Exception;
    protected abstract void closeConfigStore() throws Exception;

    public void testRecordXid() throws Exception
    {
        Record enqueueRecord = getTestRecord(1);
        Record dequeueRecord = getTestRecord(2);
        Record[] enqueues = { enqueueRecord };
        Record[] dequeues = { dequeueRecord };
        byte[] globalId = new byte[] { 1 };
        byte[] branchId = new byte[] { 2 };

        Transaction transaction = _messageStore.newTransaction();
        transaction.recordXid(1l, globalId, branchId, enqueues, dequeues);
        transaction.commitTran();
        reopenStore();
        verify(_dtxRecordRecoveryHandler).dtxRecord(1l, globalId, branchId, enqueues, dequeues);

        transaction = _messageStore.newTransaction();
        transaction.removeXid(1l, globalId, branchId);
        transaction.commitTran();

        reopenStore();
        verify(_dtxRecordRecoveryHandler, times(1)).dtxRecord(1l, globalId, branchId, enqueues, dequeues);
    }

    private Record getTestRecord(long messageNumber)
    {
        UUID queueId1 = UUIDGenerator.generateRandomUUID();
        TransactionLogResource queue1 = mock(TransactionLogResource.class);
        when(queue1.getId()).thenReturn(queueId1);
        EnqueueableMessage message1 = mock(EnqueueableMessage.class);
        when(message1.isPersistent()).thenReturn(true);
        when(message1.getMessageNumber()).thenReturn(messageNumber);
        final StoredMessage storedMessage = mock(StoredMessage.class);
        when(storedMessage.getMessageNumber()).thenReturn(messageNumber);
        when(message1.getStoredMessage()).thenReturn(storedMessage);
        Record enqueueRecord = new TestRecord(queue1, message1);
        return enqueueRecord;
    }

    private static class TestRecord implements Record
    {
        private TransactionLogResource _queue;
        private EnqueueableMessage _message;

        public TestRecord(TransactionLogResource queue, EnqueueableMessage message)
        {
            super();
            _queue = queue;
            _message = message;
        }

        @Override
        public TransactionLogResource getQueue()
        {
            return _queue;
        }

        @Override
        public EnqueueableMessage getMessage()
        {
            return _message;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_message == null) ? 0 : new Long(_message.getMessageNumber()).hashCode());
            result = prime * result + ((_queue == null) ? 0 : _queue.getId().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (!(obj instanceof Record))
            {
                return false;
            }
            Record other = (Record) obj;
            if (_message == null && other.getMessage() != null)
            {
                return false;
            }
            if (_queue == null && other.getQueue() != null)
            {
                return false;
            }
            if (_message.getMessageNumber() != other.getMessage().getMessageNumber())
            {
                return false;
            }
            return _queue.getId().equals(other.getQueue().getId());
        }

    }
}
