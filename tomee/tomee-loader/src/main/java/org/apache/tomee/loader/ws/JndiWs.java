/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomee.loader.ws;


import org.apache.openejb.AppContext;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.tomee.loader.dto.JndiDTO;

import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.ArrayList;
import java.util.List;

@Path("/ws/jndi")
@Produces({"application/json"})
public class JndiWs {

    @Path("/names")
    @GET
    public List<JndiDTO> get() throws NamingException {
        List<JndiDTO> result = new ArrayList<JndiDTO>();
        List<Object> objects = new ArrayList<Object>();


        final ContainerSystem containerSystem = SystemInstance.get().getComponent(ContainerSystem.class);

        mountJndiList(result, containerSystem.getJNDIContext(), "containerSystem", "java:global");

        List<AppContext> appCtxs = containerSystem.getAppContexts();
        for (AppContext appContext : appCtxs) {
            final Context ctx = appContext.getAppJndiContext();
            mountJndiList(result, ctx, appContext.getId(), "java:comp");
            mountJndiList(result, ctx, appContext.getId(), "java:app");
        }

        return result;
    }


    private void mountJndiList(List<JndiDTO> jndi, Context context, String id, String root) throws NamingException {
        final NamingEnumeration namingEnumeration;
        try {
            namingEnumeration = context.list(root);
        } catch (NamingException e) {
            //not found?
            return;
        }
        while (namingEnumeration.hasMoreElements()) {
            final NameClassPair pair = (NameClassPair) namingEnumeration.next();
            final String key = root + "/" + pair.getName();
            final Object obj;
            try {
                obj = context.lookup(key);
            } catch (NamingException e) {
                //not found?
                continue;
            }

            if (Context.class.isInstance(obj)) {
                mountJndiList(jndi, Context.class.cast(obj), id, key);
            } else {
                JndiDTO dto = new JndiDTO();
                dto.module = id;
                dto.name = key;
                dto.value = String.valueOf(obj);
                jndi.add(dto);
            }
        }

    }

}