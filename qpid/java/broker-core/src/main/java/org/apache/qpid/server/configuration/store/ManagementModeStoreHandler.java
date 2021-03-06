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
package org.apache.qpid.server.configuration.store;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.util.MapValueConverter;

public class ManagementModeStoreHandler implements ConfigurationEntryStore
{
    private static final Logger LOGGER = Logger.getLogger(ManagementModeStoreHandler.class);

    private static final String MANAGEMENT_MODE_PORT_PREFIX = "MANAGEMENT-MODE-PORT-";
    private static final String PORT_TYPE = Port.class.getSimpleName();
    private static final String VIRTUAL_HOST_TYPE = VirtualHost.class.getSimpleName();
    private static final String ATTRIBUTE_STATE = VirtualHost.STATE;
    private static final Object MANAGEMENT_MODE_AUTH_PROVIDER = "mm-auth";


    private final ConfigurationEntryStore _store;
    private final Map<UUID, ConfigurationEntry> _cliEntries;
    private final Map<UUID, Object> _quiescedEntries;
    private final UUID _rootId;

    public ManagementModeStoreHandler(ConfigurationEntryStore store, BrokerOptions options)
    {
        ConfigurationEntry storeRoot = store.getRootEntry();
        _store = store;
        _rootId = storeRoot.getId();
        _cliEntries = createPortsFromCommandLineOptions(options);
        _quiescedEntries = quiesceEntries(storeRoot, options);
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        return getEntry(_rootId);
    }

    @Override
    public ConfigurationEntry getEntry(UUID id)
    {
        synchronized (_store)
        {
            if (_cliEntries.containsKey(id))
            {
                return _cliEntries.get(id);
            }

            ConfigurationEntry entry = _store.getEntry(id);
            if (_quiescedEntries.containsKey(id))
            {
                entry = createEntryWithState(entry, State.QUIESCED);
            }
            else if (id == _rootId)
            {
                entry = createRootWithCLIEntries(entry);
            }
            return entry;
        }
    }

    @Override
    public void save(ConfigurationEntry... entries)
    {
        synchronized (_store)
        {
            ConfigurationEntry[] entriesToSave = new ConfigurationEntry[entries.length];

            for (int i = 0; i < entries.length; i++)
            {
                ConfigurationEntry entry = entries[i];
                UUID id = entry.getId();
                if (_cliEntries.containsKey(id))
                {
                    throw new IllegalConfigurationException("Cannot save configuration provided as command line argument:"
                            + entry);
                }
                else if (_quiescedEntries.containsKey(id))
                {
                    // save entry with the original state
                    entry = createEntryWithState(entry, _quiescedEntries.get(ATTRIBUTE_STATE));
                }
                else if (_rootId.equals(id))
                {
                    // save root without command line entries
                    Set<UUID> childrenIds = new HashSet<UUID>(entry.getChildrenIds());
                    if (!_cliEntries.isEmpty())
                    {
                        childrenIds.removeAll(_cliEntries.entrySet());
                    }
                    HashMap<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
                    entry = new ConfigurationEntry(entry.getId(), entry.getType(), attributes, childrenIds, this);
                }
                entriesToSave[i] = entry;
            }

            _store.save(entriesToSave);
        }
    }

    @Override
    public UUID[] remove(UUID... entryIds)
    {
        synchronized (_store)
        {
            for (UUID id : entryIds)
            {
                if (_cliEntries.containsKey(id))
                {
                    throw new IllegalConfigurationException("Cannot change configuration for command line entry:"
                            + _cliEntries.get(id));
                }
            }
            UUID[] result = _store.remove(entryIds);
            for (UUID id : entryIds)
            {
                if (_quiescedEntries.containsKey(id))
                {
                    _quiescedEntries.remove(id);
                }
            }
            return result;
        }
    }

    @Override
    public void copyTo(String copyLocation)
    {
        synchronized (_store)
        {
            _store.copyTo(copyLocation);
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _store.getStoreLocation();
    }

    @Override
    public int getVersion()
    {
        return _store.getVersion();
    }

    @Override
    public String getType()
    {
        return _store.getType();
    }

    private Map<UUID, ConfigurationEntry> createPortsFromCommandLineOptions(BrokerOptions options)
    {
        int managementModeRmiPortOverride = options.getManagementModeRmiPortOverride();
        if (managementModeRmiPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid rmi port is specified: " + managementModeRmiPortOverride);
        }
        int managementModeJmxPortOverride = options.getManagementModeJmxPortOverride();
        if (managementModeJmxPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid jmx port is specified: " + managementModeJmxPortOverride);
        }
        int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();
        if (managementModeHttpPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid http port is specified: " + managementModeHttpPortOverride);
        }
        Map<UUID, ConfigurationEntry> cliEntries = new HashMap<UUID, ConfigurationEntry>();
        if (managementModeRmiPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeRmiPortOverride, Protocol.RMI);
            cliEntries.put(entry.getId(), entry);
            if (managementModeJmxPortOverride == 0)
            {
                ConfigurationEntry connectorEntry = createCLIPortEntry(managementModeRmiPortOverride + 100, Protocol.JMX_RMI);
                cliEntries.put(connectorEntry.getId(), connectorEntry);
            }
        }
        if (managementModeJmxPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeJmxPortOverride, Protocol.JMX_RMI);
            cliEntries.put(entry.getId(), entry);
        }
        if (managementModeHttpPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeHttpPortOverride, Protocol.HTTP);
            cliEntries.put(entry.getId(), entry);
        }
        return cliEntries;
    }

    private ConfigurationEntry createCLIPortEntry(int port, Protocol protocol)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, port);
        attributes.put(Port.PROTOCOLS, Collections.singleton(protocol));
        attributes.put(Port.NAME, MANAGEMENT_MODE_PORT_PREFIX + protocol.name());
        if (protocol != Protocol.RMI)
        {
            attributes.put(Port.AUTHENTICATION_PROVIDER, MANAGEMENT_MODE_AUTH_PROVIDER);
        }
        ConfigurationEntry portEntry = new ConfigurationEntry(UUID.randomUUID(), PORT_TYPE, attributes,
                Collections.<UUID> emptySet(), this);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Add management mode port configuration " + portEntry + " for port " + port + " and protocol "
                    + protocol);
        }
        return portEntry;
    }

    private ConfigurationEntry createRootWithCLIEntries(ConfigurationEntry storeRoot)
    {
        Set<UUID> childrenIds = new HashSet<UUID>(storeRoot.getChildrenIds());
        if (!_cliEntries.isEmpty())
        {
            childrenIds.addAll(_cliEntries.keySet());
        }
        ConfigurationEntry root = new ConfigurationEntry(storeRoot.getId(), storeRoot.getType(), new HashMap<String, Object>(
                storeRoot.getAttributes()), childrenIds, this);
        return root;
    }

    private Map<UUID, Object> quiesceEntries(ConfigurationEntry storeRoot, BrokerOptions options)
    {
        Map<UUID, Object> quiescedEntries = new HashMap<UUID, Object>();
        Set<UUID> childrenIds;
        int managementModeRmiPortOverride = options.getManagementModeRmiPortOverride();
        int managementModeJmxPortOverride = options.getManagementModeJmxPortOverride();
        int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();
        childrenIds = storeRoot.getChildrenIds();
        for (UUID id : childrenIds)
        {
            ConfigurationEntry entry = _store.getEntry(id);
            String entryType = entry.getType();
            Map<String, Object> attributes = entry.getAttributes();
            boolean quiesce = false;
            if (VIRTUAL_HOST_TYPE.equals(entryType) && options.isManagementModeQuiesceVirtualHosts())
            {
                quiesce = true;
            }
            else if (PORT_TYPE.equals(entryType))
            {
                if (attributes == null)
                {
                    throw new IllegalConfigurationException("Port attributes are not set in " + entry);
                }
                Set<Protocol> protocols = getPortProtocolsAttribute(attributes);
                if (protocols == null)
                {
                    quiesce = true;
                }
                else
                {
                    for (Protocol protocol : protocols)
                    {
                        switch (protocol)
                        {
                        case JMX_RMI:
                            quiesce = managementModeJmxPortOverride > 0 || managementModeRmiPortOverride > 0;
                            break;
                        case RMI:
                            quiesce = managementModeRmiPortOverride > 0;
                            break;
                        case HTTP:
                            quiesce = managementModeHttpPortOverride > 0;
                            break;
                        default:
                            quiesce = true;
                        }
                    }
                }
            }
            if (quiesce)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Management mode quiescing entry " + entry);
                }

                // save original state
                quiescedEntries.put(entry.getId(), attributes.get(ATTRIBUTE_STATE));
            }
        }
        return quiescedEntries;
    }

    private Set<Protocol> getPortProtocolsAttribute(Map<String, Object> attributes)
    {
        Object object = attributes.get(Port.PROTOCOLS);
        if (object == null)
        {
            return null;
        }
        return MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
    }

    private ConfigurationEntry createEntryWithState(ConfigurationEntry entry, Object state)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        if (state == null)
        {
            attributes.remove(ATTRIBUTE_STATE);
        }
        else
        {
            attributes.put(ATTRIBUTE_STATE, state);
        }
        Set<UUID> originalChildren = entry.getChildrenIds();
        Set<UUID> children = null;
        if (originalChildren != null)
        {
            children = new HashSet<UUID>(originalChildren);
        }
        return new ConfigurationEntry(entry.getId(), entry.getType(), attributes, children, entry.getStore());
    }

}
