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

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
 *   0-9
 */

package org.apache.qpid.framing.amqp_0_9;

import org.apache.qpid.codec.MarkableDataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.framing.*;
import org.apache.qpid.AMQException;

public class ConnectionStartBodyImpl extends AMQMethodBody_0_9 implements ConnectionStartBody
{
    private static final AMQMethodBodyInstanceFactory FACTORY_INSTANCE = new AMQMethodBodyInstanceFactory()
    {
        public AMQMethodBody newInstance(MarkableDataInput in, long size) throws AMQFrameDecodingException, IOException
        {
            return new ConnectionStartBodyImpl(in);
        }
    };

    public static AMQMethodBodyInstanceFactory getFactory()
    {
        return FACTORY_INSTANCE;
    }

    public static final int CLASS_ID =  10;
    public static final int METHOD_ID = 10;

    // Fields declared in specification
    private final short _versionMajor; // [versionMajor]
    private final short _versionMinor; // [versionMinor]
    private final FieldTable _serverProperties; // [serverProperties]
    private final byte[] _mechanisms; // [mechanisms]
    private final byte[] _locales; // [locales]

    // Constructor
    public ConnectionStartBodyImpl(MarkableDataInput buffer) throws AMQFrameDecodingException, IOException
    {
        _versionMajor = readUnsignedByte( buffer );
        _versionMinor = readUnsignedByte( buffer );
        _serverProperties = readFieldTable( buffer );
        _mechanisms = readBytes( buffer );
        _locales = readBytes( buffer );
    }

    public ConnectionStartBodyImpl(
                                short versionMajor,
                                short versionMinor,
                                FieldTable serverProperties,
                                byte[] mechanisms,
                                byte[] locales
                            )
    {
        _versionMajor = versionMajor;
        _versionMinor = versionMinor;
        _serverProperties = serverProperties;
        _mechanisms = mechanisms;
        _locales = locales;
    }

    public int getClazz()
    {
        return CLASS_ID;
    }

    public int getMethod()
    {
        return METHOD_ID;
    }

    public final short getVersionMajor()
    {
        return _versionMajor;
    }
    public final short getVersionMinor()
    {
        return _versionMinor;
    }
    public final FieldTable getServerProperties()
    {
        return _serverProperties;
    }
    public final byte[] getMechanisms()
    {
        return _mechanisms;
    }
    public final byte[] getLocales()
    {
        return _locales;
    }

    protected int getBodySize()
    {
        int size = 2;
        size += getSizeOf( _serverProperties );
        size += getSizeOf( _mechanisms );
        size += getSizeOf( _locales );
        return size;
    }

    public void writeMethodPayload(DataOutput buffer) throws IOException
    {
        writeUnsignedByte( buffer, _versionMajor );
        writeUnsignedByte( buffer, _versionMinor );
        writeFieldTable( buffer, _serverProperties );
        writeBytes( buffer, _mechanisms );
        writeBytes( buffer, _locales );
    }

    public boolean execute(MethodDispatcher dispatcher, int channelId) throws AMQException
	{
    return ((MethodDispatcher_0_9)dispatcher).dispatchConnectionStart(this, channelId);
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder("[ConnectionStartBodyImpl: ");
        buf.append( "versionMajor=" );
        buf.append(  getVersionMajor() );
        buf.append( ", " );
        buf.append( "versionMinor=" );
        buf.append(  getVersionMinor() );
        buf.append( ", " );
        buf.append( "serverProperties=" );
        buf.append(  getServerProperties() );
        buf.append( ", " );
        buf.append( "mechanisms=" );
        buf.append(  getMechanisms() == null  ? "null" : java.util.Arrays.toString( getMechanisms() ) );
        buf.append( ", " );
        buf.append( "locales=" );
        buf.append(  getLocales() == null  ? "null" : java.util.Arrays.toString( getLocales() ) );
        buf.append("]");
        return buf.toString();
    }

}
