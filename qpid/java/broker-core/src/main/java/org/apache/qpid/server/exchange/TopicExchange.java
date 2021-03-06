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
package org.apache.qpid.server.exchange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.topic.TopicExchangeResult;
import org.apache.qpid.server.exchange.topic.TopicMatcherResult;
import org.apache.qpid.server.exchange.topic.TopicNormalizer;
import org.apache.qpid.server.exchange.topic.TopicParser;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;

public class TopicExchange extends AbstractExchange
{
    public static final ExchangeType<TopicExchange> TYPE = new TopicExchangeType();


    private static final Logger _logger = Logger.getLogger(TopicExchange.class);

    private final TopicParser _parser = new TopicParser();

    private final Map<String, TopicExchangeResult> _topicExchangeResults =
            new ConcurrentHashMap<String, TopicExchangeResult>();

    private final Map<Binding, Map<String,Object>> _bindings = new HashMap<Binding, Map<String,Object>>();

    public TopicExchange()
    {
        super(TYPE);
    }

    protected synchronized void registerQueue(final Binding binding) throws AMQInvalidArgumentException
    {
        final String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getQueue();
        Map<String,Object> args = binding.getArguments();

        assert queue != null;
        assert bindingKey != null;

        _logger.debug("Registering queue " + queue.getName() + " with routing key " + bindingKey);


        String routingKey = TopicNormalizer.normalize(bindingKey);

        if(_bindings.containsKey(binding))
        {
            Map<String,Object> oldArgs = _bindings.get(binding);
            TopicExchangeResult result = _topicExchangeResults.get(routingKey);

            if(FilterSupport.argumentsContainFilter(args))
            {
                if(FilterSupport.argumentsContainFilter(oldArgs))
                {
                    result.replaceQueueFilter(queue,
                                              FilterSupport.createMessageFilter(oldArgs, queue),
                                              FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                    result.removeUnfilteredQueue(queue);
                }
            }
            else
            {
                if(FilterSupport.argumentsContainFilter(oldArgs))
                {
                    result.addUnfilteredQueue(queue);
                    result.removeFilteredQueue(queue, FilterSupport.createMessageFilter(oldArgs, queue));
                }
                else
                {
                    // TODO - fix control flow
                    return;
                }
            }

            result.addBinding(binding);

        }
        else
        {

            TopicExchangeResult result = _topicExchangeResults.get(routingKey);
            if(result == null)
            {
                result = new TopicExchangeResult();
                if(FilterSupport.argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addUnfilteredQueue(queue);
                }
                _parser.addBinding(routingKey, result);
                _topicExchangeResults.put(routingKey,result);
            }
            else
            {
                if(FilterSupport.argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addUnfilteredQueue(queue);
                }
            }

            result.addBinding(binding);
            _bindings.put(binding, args);
        }

    }

    @Override
    public ArrayList<BaseQueue> doRoute(ServerMessage payload, final InstanceProperties instanceProperties)
    {

        final String routingKey = payload.getRoutingKey() == null
                                          ? ""
                                          : payload.getRoutingKey();

        final Collection<AMQQueue> matchedQueues =
                getMatchedQueues(Filterable.Factory.newInstance(payload,instanceProperties), routingKey);

        ArrayList<BaseQueue> queues;

        if(matchedQueues.getClass() == ArrayList.class)
        {
            queues = (ArrayList) matchedQueues;
        }
        else
        {
            queues = new ArrayList<BaseQueue>();
            queues.addAll(matchedQueues);
        }

        if(queues == null || queues.isEmpty())
        {
            _logger.info("Message routing key: " + payload.getRoutingKey() + " No routes.");
        }

        return queues;

    }

    private boolean deregisterQueue(final Binding binding)
    {
        if(_bindings.containsKey(binding))
        {
            Map<String,Object> bindingArgs = _bindings.remove(binding);
            String bindingKey = TopicNormalizer.normalize(binding.getBindingKey());
            TopicExchangeResult result = _topicExchangeResults.get(bindingKey);

            result.removeBinding(binding);

            if(FilterSupport.argumentsContainFilter(bindingArgs))
            {
                try
                {
                    result.removeFilteredQueue(binding.getQueue(), FilterSupport.createMessageFilter(bindingArgs,
                            binding.getQueue()));
                }
                catch (AMQInvalidArgumentException e)
                {
                    return false;
                }
            }
            else
            {
                result.removeUnfilteredQueue(binding.getQueue());
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    private Collection<AMQQueue> getMatchedQueues(Filterable message, String routingKey)
    {

        Collection<TopicMatcherResult> results = _parser.parse(routingKey);
        switch(results.size())
        {
            case 0:
                return Collections.EMPTY_SET;
            case 1:
                TopicMatcherResult[] resultQueues = new TopicMatcherResult[1];
                results.toArray(resultQueues);
                return ((TopicExchangeResult)resultQueues[0]).processMessage(message, null);
            default:
                Collection<AMQQueue> queues = new HashSet<AMQQueue>();
                for(TopicMatcherResult result : results)
                {
                    TopicExchangeResult res = (TopicExchangeResult)result;

                    for(Binding b : res.getBindings())
                    {
                        b.incrementMatches();
                    }

                    queues = res.processMessage(message, queues);
                }
                return queues;
        }


    }

    protected void onBind(final Binding binding)
    {
        try
        {
            registerQueue(binding);
        }
        catch (AMQInvalidArgumentException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void onUnbind(final Binding binding)
    {
        deregisterQueue(binding);
    }

}
