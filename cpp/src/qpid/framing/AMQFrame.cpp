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
#include "AMQFrame.h"

#include "qpid/framing/variant.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/reply_exceptions.h"

#include <boost/format.hpp>

#include <iostream>

namespace qpid {
namespace framing {

AMQFrame::~AMQFrame() {}

void AMQFrame::setBody(const AMQBody& b) { body = new BodyHolder(b); }

void AMQFrame::setMethod(ClassId c, MethodId m) { body = new BodyHolder(c,m); }

// This is now misleadingly named as it is not the frame size as
// defined in the spec (as it also includes the end marker)
uint32_t AMQFrame::size() const {
    return frameOverhead() + body->size();
}

uint32_t AMQFrame::frameOverhead() {
    return 12 /*frame header*/ + 1/*0xCE*/;
}

void AMQFrame::encode(Buffer& buffer) const
{
    uint8_t flags = (bof ? 0x08 : 0) | (eof ? 0x04 : 0) | (bos ? 0x02 : 0) | (eos ? 0x01 : 0);
    buffer.putOctet(flags);
    buffer.putOctet(getBody()->type());
    buffer.putShort(size() - 1); // Don't include end marker (it's not part of the frame itself)
    buffer.putOctet(0);
    buffer.putOctet(0x0f & subchannel);
    buffer.putShort(channel);    
    buffer.putLong(0);
    body->encode(buffer);
    buffer.putOctet(0xCE);
}

bool AMQFrame::decode(Buffer& buffer)
{    
    if(buffer.available() < frameOverhead() - 1)
        return false;
    buffer.record();

    uint8_t  flags = buffer.getOctet();
    uint8_t framing_version = (flags & 0xc0) >> 6;
    if (framing_version != 0)
        throw SyntaxErrorException(QPID_MSG("Framing version unsupported"));
    bof = flags & 0x08;
    eof = flags & 0x04;
    bos = flags & 0x02;
    eos = flags & 0x01;
    uint8_t  type = buffer.getOctet();
    uint16_t frame_size =  buffer.getShort();
    if (frame_size < frameOverhead()-1)
        throw SyntaxErrorException(QPID_MSG("Frame size too small"));    
    uint8_t  reserved1 = buffer.getOctet();
    uint8_t  field1 = buffer.getOctet();
    subchannel = field1 & 0x0f;
    channel = buffer.getShort();
    (void) buffer.getLong(); // reserved2
    
    // Verify that the protocol header meets current spec
    // TODO: should we check reserved2 against zero as well? - the
    // spec isn't clear
    if ((flags & 0x30) != 0 || reserved1 != 0 || (field1 & 0xf0) != 0)
        throw SyntaxErrorException(QPID_MSG("Reserved bits not zero"));

    // TODO: should no longer care about body size and only pass up
    // B,E,b,e flags
    uint16_t body_size = frame_size + 1 - frameOverhead(); 
    if (buffer.available() < body_size+1u){
        buffer.restore();
        return false;
    }
    body = new BodyHolder();
    body->decode(type,buffer, body_size);
    uint8_t end = buffer.getOctet();
    if (end != 0xCE)
        throw SyntaxErrorException(QPID_MSG("Frame end not found"));
    return true;
}

std::ostream& operator<<(std::ostream& out, const AMQFrame& f)
{
    return
        out << "Frame[" 
            << (f.getBof() ? "B" : "") << (f.getEof() ? "E" : "")
            << (f.getBos() ? "b" : "") << (f.getEos() ? "e" : "") << "; "
            << "channel=" << f.getChannel() << "; " << *f.getBody()
            << "]";
}


}} // namespace qpid::framing
