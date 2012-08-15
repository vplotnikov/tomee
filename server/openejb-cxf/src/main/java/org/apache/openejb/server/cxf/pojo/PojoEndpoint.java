/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.server.cxf.pojo;

import org.apache.cxf.Bus;
import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.jaxws.JAXWSMethodInvoker;
import org.apache.cxf.jaxws.context.WebServiceContextResourceResolver;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.resource.DefaultResourceManager;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.resource.ResourceResolver;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.openejb.InjectionProcessor;
import org.apache.openejb.core.webservices.JaxWsUtils;
import org.apache.openejb.core.webservices.PortData;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.server.cxf.CxfEndpoint;
import org.apache.openejb.server.cxf.CxfService;
import org.apache.openejb.server.cxf.CxfServiceConfiguration;
import org.apache.openejb.server.cxf.JaxWsImplementorInfoImpl;

import javax.naming.Context;
import javax.xml.ws.WebServiceException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.openejb.InjectionProcessor.unwrap;

public class PojoEndpoint extends CxfEndpoint {
    public static final String OPENEJB_JAXWS_READ_POJO_PROPERTIES = "openejb.jaxws.read-pojo-properties"; // boolean to force it (perf reason)
    public static final String OPENEJB_JAXWS_POJO_CONFIG_PREFIX = "openejb.jaxws.pojo.config."; // prefix to avoid conflicts

    private InjectionProcessor<Object> injectionProcessor;

    public PojoEndpoint(Bus bus, PortData port, Context context, Class<?> instance, HTTPTransportFactory httpTransportFactory, Map<String, Object> bindings) {
    	super(bus, port, context, instance, httpTransportFactory);

        String bindingURI = null;
        if (port.getBindingID() != null) {
            bindingURI = JaxWsUtils.getBindingURI(port.getBindingID());
        }
        implInfo = new JaxWsImplementorInfoImpl(instance, bindingURI);

        serviceFactory = new JaxWsServiceFactoryBean(implInfo);
        serviceFactory.setBus(bus);

        // install as first to overwrite annotations (wsdl-file, wsdl-port, wsdl-service)
        CxfServiceConfiguration configuration = new CxfServiceConfiguration(port);
        serviceFactory.getConfigurations().add(0, configuration);

        service = serviceFactory.create();

        // instantiate and inject resources into service
        try {
            injectionProcessor = new InjectionProcessor<Object>(instance, port.getInjections(), null, null, unwrap(context), bindings);
            injectionProcessor.createInstance();
            injectionProcessor.postConstruct();
            implementor = injectionProcessor.getInstance();
            injectCxfResources(implementor);
        } catch (Exception e) {
            throw new WebServiceException("Service resource injection failed", e);
        }

        service.setInvoker(new JAXWSMethodInvoker(implementor));
    }

    private void injectCxfResources(final Object implementor) {
        ResourceManager resourceManager = bus.getExtension(ResourceManager.class);
        List<ResourceResolver> resolvers = resourceManager.getResourceResolvers();
        resourceManager = new DefaultResourceManager(resolvers);
        resourceManager.addResourceResolver(new WebServiceContextResourceResolver());
        ResourceInjector injector = new ResourceInjector(resourceManager);
        injector.inject(implementor);
    }

    @Override
    protected Properties getFeaturesProperties() {
        if (SystemInstance.get().getOptions().get(OPENEJB_JAXWS_READ_POJO_PROPERTIES, false)) {
            return null;
        }
        return SystemInstance.get().getProperties();
    }

    @Override
    protected Map<String, Object> getEndpointProperties() {
        if (SystemInstance.get().getOptions().get(OPENEJB_JAXWS_READ_POJO_PROPERTIES, false)) {
            final String prefix = OPENEJB_JAXWS_POJO_CONFIG_PREFIX + getImplementorClass().getName() + ".";
            final Map<String, Object> map = new HashMap<String, Object>();
            for (Map.Entry<Object, Object> entry : SystemInstance.get().getProperties().entrySet()) {
                final String key = entry.getKey().toString();
                if (key.startsWith(prefix)) {
                    map.put(key.substring(prefix.length()), entry.getValue());
                }
            }
            return map;
        }
        return null;
    }

    protected void init() {
        // configure and inject handlers
        try {
            initHandlers();
        } catch (Exception e) {
            throw new WebServiceException("Error configuring handlers", e);
        }
    }

    public void stop() {
        // call handler preDestroy
        destroyHandlers();

        // call service preDestroy
        if (injectionProcessor != null) {
            injectionProcessor.preDestroy();
        }

        // shutdown server
        super.stop();
    }

    @Override
    protected String getFeaturePropertyKey() {
        return getImplementorClass().getName() + "." + CxfService.OPENEJB_JAXWS_CXF_FEATURES;
    }
}
