/*
 * Copyright © 2016 Copyright(c) Northwestern University, LIST Team and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.DisplayString;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.Toaster;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.ToasterBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class ToasterProvider implements BindingAwareProvider, DataChangeListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterProvider.class);

    private ProviderContext providerContext;
    private DataBroker dataService;
    private ListenerRegistration<DataChangeListener> dcReg;

    public static final InstanceIdentifier<Toaster> TOASTER_ID = InstanceIdentifier.builder(Toaster.class).build();
    private static final DisplayString TOASTER_MANUFACTURER = new DisplayString("Northwestern University, LIST Team");
    private static final DisplayString TOASTER_MODEL_NUMBER = new DisplayString("Model X, Binding Aware");

    @Override
    public void onSessionInitiated(ProviderContext session) {
        providerContext = session;
        dataService = session.getSALService(DataBroker.class);
        dcReg = dataService.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, TOASTER_ID, this, DataChangeScope.SUBTREE);

        Toaster toaster = new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURER)
                .setToasterModelNumber(TOASTER_MODEL_NUMBER).setToasterStatus(Toaster.ToasterStatus.Up).build();
        WriteTransaction wtx = dataService.newWriteOnlyTransaction();
        wtx.put(LogicalDatastoreType.OPERATIONAL, TOASTER_ID, toaster);
        Futures.addCallback(wtx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.info("OperationalInit Succeed");
            }
            @Override
            public void onFailure(Throwable t) {
                LOG.info("OperationalInit Failed");
            }
        });
        LOG.info("Init Toaster Operational: {}", toaster);

        LOG.info("ToasterProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        if (dcReg != null) {
            dcReg.close();
        }
        LOG.info("ToasterProvider Closed");
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
        DataObject d = asyncDataChangeEvent.getUpdatedSubtree();
        if (d instanceof Toaster) {
            Toaster t = (Toaster) d;
            LOG.info("onDataChange - new Toaster config: ", t);
        } else {
            LOG.info("onDataChange - not Toaster config: ", d);
        }
    }
}
