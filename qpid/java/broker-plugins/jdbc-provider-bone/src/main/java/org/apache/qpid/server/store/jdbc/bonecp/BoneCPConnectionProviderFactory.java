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
package org.apache.qpid.server.store.jdbc.bonecp;

import java.sql.SQLException;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.store.jdbc.ConnectionProvider;

public class BoneCPConnectionProviderFactory implements JDBCConnectionProviderFactory
{
    @Override
    public String getType()
    {
        return "BONECP";
    }

    @Override
    public ConnectionProvider getConnectionProvider(String connectionUrl, VirtualHost virtualHost)
            throws SQLException
    {
        return new BoneCPConnectionProvider(connectionUrl, virtualHost);
    }
}
