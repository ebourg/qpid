package org.apache.qpid.configuration;
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


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public interface Accessor
{
    public Boolean getBoolean(String name);
    public Integer getInt(String name);
    public Long getLong(String name);
    public String getString(String name);

    static class MapAccessor implements Accessor
    {
        protected Map<Object,Object> source;
        
        public MapAccessor(Map<Object,Object> map)
        {
            source = map;
        }
        
        public Boolean getBoolean(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Boolean)
                {
                    return (Boolean)source.get(name);
                }
                else
                {
                    return Boolean.parseBoolean((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public Integer getInt(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Integer)
                {
                    return (Integer)source.get(name);
                }
                else
                {
                    return Integer.parseInt((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public Long getLong(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Long)
                {
                    return (Long)source.get(name);
                }
                else
                {
                    return Long.parseLong((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public String getString(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof String)
                {
                    return (String)source.get(name);
                }
                else
                {
                    return String.valueOf(source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
    }

}
