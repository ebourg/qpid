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
package org.apache.qpid.ra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.transaction.TransactionManager;

import org.apache.qpid.ra.admin.QpidQueue;
import org.apache.qpid.ra.admin.QpidTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Various utility functions
 *
 */
public class Util
{

   private static final Logger _log = LoggerFactory.getLogger(Util.class);

   /**
    * Compare two strings.
    * @param me First value
    * @param you Second value
    * @return True if object equals else false.
    */
   public static boolean compare(final String me, final String you)
   {
      // If both null or intern equals
      if (me == you)
      {
         return true;
      }

      // if me null and you are not
      if (me == null && you != null)
      {
         return false;
      }

      // me will not be null, test for equality
      return me.equals(you);
   }

   /**
    * Lookup an object in the default initial context
    * @param context The context to use
    * @param name the name to lookup
    * @param clazz the expected type
    * @return the object
    * @throws Exception for any error
    */
   public static <T> T lookup(final Context context, final String name, final Class<T> clazz) throws Exception
   {
	   Object object = context.lookup(name);

	   if (object instanceof Reference)
       {

           Reference ref = (Reference) object;
           String addressContent = null;

           if (ref.getClassName().equals(QpidQueue.class.getName()))
           {
               RefAddr addr = ref.get(QpidQueue.class.getName());
               addressContent = (String) addr.getContent();

               if (addr != null)
               {
                   return (T)new QpidQueue(addressContent);
               }
           }

           if (ref.getClassName().equals(QpidTopic.class.getName()))
           {
               RefAddr addr = ref.get(QpidTopic.class.getName());
               addressContent = (String) addr.getContent();

               if (addr != null)
               {
                   return (T)new QpidTopic(addressContent);
               }
           }
       }

	   return clazz.cast(object);

   }

   /** The Resource adapter can't depend on any provider's specific library. Because of that we use reflection to locate the
    *  transaction manager during startup.
    *
    *
    *  TODO: We should use a proper SPI instead of reflection
    *        We would need to define a proper SPI package for this.
    **/
   public static TransactionManager locateTM(final String locatorClass, final String locatorMethod)
   {
      try
      {
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         Class<?> aClass = loader.loadClass(locatorClass);
         Object o = aClass.newInstance();
         Method m = aClass.getMethod(locatorMethod);
         return (TransactionManager)m.invoke(o);
      }
      catch (Throwable e)
      {
         _log.debug(e.getMessage(), e);
         return null;
      }
   }

   /**
    * Serialize the object into a byte array.
    * @param serializable The serializable object
    * @return The generated byte array
    * @throws IOException For errors during serialization.
    */
   public static byte[] serialize(final Serializable serializable)
      throws IOException
   {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
      final ObjectOutputStream oos = new ObjectOutputStream(baos) ;
      oos.writeObject(serializable) ;
      oos.close() ;
      return baos.toByteArray() ;
   }

   /**
    * Deserialize the byte array into an object.
    * @param data The serialized object as a byte array
    * @return The serializable object.
    * @throws IOException For errors during deserialization
    * @throws ClassNotFoundException If the deserialized class cannot be found.
    */
   public static Object deserialize(final byte[] data)
      throws IOException, ClassNotFoundException
   {
      final ByteArrayInputStream bais = new ByteArrayInputStream(data) ;
      final ObjectInputStream ois = new ObjectInputStream(bais) ;
      return ois.readObject() ;
   }

   /**
    * Return a string identification for the specified object.
    * @param object The object value.
    * @return The string identification.
    */
   public static String asString(final Object object)
   {
      return (object == null ? "null" : object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object))) ;
   }
}