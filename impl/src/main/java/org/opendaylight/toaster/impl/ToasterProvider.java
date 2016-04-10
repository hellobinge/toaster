/*
 * Copyright © 2016 Copyright(c) Northwestern University, LIST Team and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.impl.rev141210.ToasterRuntimeMXBean;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.*;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ToasterProvider implements BindingAwareProvider, ToasterService, ToasterRuntimeMXBean, DataChangeListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterProvider.class);

    private NotificationProviderService notificationService;
    private ProviderContext providerContext;
    private DataBroker dataService;
    private ListenerRegistration<DataChangeListener> dcReg;
    private BindingAwareBroker.RpcRegistration<ToasterService> rpcReg;

    public static final InstanceIdentifier<Toaster> TOASTER_ID = InstanceIdentifier.builder(Toaster.class).build();
    private static final DisplayString TOASTER_MANUFACTURER = new DisplayString("Northwestern University, LIST Team");
    private static final DisplayString TOASTER_MODEL_NUMBER = new DisplayString("Model X, Binding Aware");

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicReference<Future<?>> currentMakeToastTask = new AtomicReference<>();
    private final AtomicLong amountOfBreadInStock = new AtomicLong(100);
    private final AtomicLong darknessFactor = new AtomicLong(1000);
    private final AtomicLong toastsMade = new AtomicLong(0);

    @Override
    public void onSessionInitiated(ProviderContext session) {
        providerContext = session;
        notificationService = providerContext.getSALService(NotificationProviderService.class);
        dataService = providerContext.getSALService(DataBroker.class);
        dcReg = dataService.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, TOASTER_ID, this, DataChangeScope.SUBTREE);
        rpcReg = providerContext.addRpcImplementation(ToasterService.class, this);

        Toaster t1 = new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURER)
                .setToasterModelNumber(TOASTER_MODEL_NUMBER).setToasterStatus(Toaster.ToasterStatus.Up).build();
        WriteTransaction wtx1 = dataService.newWriteOnlyTransaction();
        wtx1.put(LogicalDatastoreType.OPERATIONAL, TOASTER_ID, t1);
        Futures.addCallback(wtx1.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.info("OperationalInit Succeed: {}", result);
            }
            @Override
            public void onFailure(Throwable t) {
                LOG.info("OperationalInit Fail: {}", t);
            }
        });
        LOG.info("Init Toaster Operational: {}", t1);

        Toaster t2 = new ToasterBuilder().setDarknessFactor(darknessFactor.get()).build();
        WriteTransaction wtx2 = dataService.newWriteOnlyTransaction();
        wtx2.put(LogicalDatastoreType.CONFIGURATION, TOASTER_ID, t2);
        Futures.addCallback(wtx2.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                LOG.info("ConfigurationInit Succeed: {}", result);
            }
            @Override
            public void onFailure(Throwable t) {
                LOG.info("ConfigurationInit Fail: {}", t);
            }
        });
        LOG.info("Init Toaster Configurational: {}", t2);

        LOG.info("ToasterProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        if (dataService != null) {
            WriteTransaction tx = dataService.newWriteOnlyTransaction();
            tx.delete(LogicalDatastoreType.OPERATIONAL, TOASTER_ID);
            Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    LOG.debug("Delete Toaster: {}", result);
                }
                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Fail to Delte Toaster: {}", t);
                }
            });
        }
        if (dcReg != null) {
            dcReg.close();
        }
        if (rpcReg != null) {
            rpcReg.close();
        }
        LOG.info("ToasterProvider Closed");
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
        DataObject d = asyncDataChangeEvent.getUpdatedSubtree();
        if (d instanceof Toaster) {
            Toaster t = (Toaster) d;
            Long darkness = t.getDarknessFactor();
            if (darkness != null) {
                darknessFactor.set(darkness);
            }
            LOG.info("onDataChange - new Toaster config: {}", t);
        } else {
            LOG.info("onDataChange - not Toaster config: {}", d);
        }
    }

    @Override
    public Future<RpcResult<Void>> cancelToast() {
        LOG.info("Cancel Toast");
        Future<?> current = currentMakeToastTask.getAndSet(null);
        if (current != null) {
            current.cancel(true);
        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    @Override
    public Future<RpcResult<Void>> makeToast(MakeToastInput input) {
        LOG.info("Make Toast");
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        checkStatusAndMakeToast(input, futureResult, 2);
        return futureResult;
    }

    @Override
    public Future<RpcResult<Void>> restockToaster(RestockToasterInput input) {
        LOG.info("Restock Toaster");
        amountOfBreadInStock.set(input.getAmountOfBreadToStock());
        if (amountOfBreadInStock.get() > 0) {
            ToasterRestocked reStockedNotification = new ToasterRestockedBuilder()
                    .setAmountOfBread(input.getAmountOfBreadToStock()).build();
            notificationService.publish(reStockedNotification);
        }
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    private void checkStatusAndMakeToast(MakeToastInput input, SettableFuture<RpcResult<Void>> futureResult,
                                         int tries) {
        LOG.info("Check Status And Make Toast");
        final ReadWriteTransaction tx = dataService.newReadWriteTransaction();
        ListenableFuture<Optional<Toaster>> readFuture = tx.read(LogicalDatastoreType.OPERATIONAL, TOASTER_ID);
        final ListenableFuture<Void> commitFuture = Futures.transform(readFuture, new AsyncFunction<Optional<Toaster>, Void>() {
            @Override
            public ListenableFuture<Void> apply(Optional<Toaster> toasterData) throws Exception {
                Toaster.ToasterStatus toasterStatus = Toaster.ToasterStatus.Up;
                if (toasterData.isPresent()) {
                    toasterStatus = toasterData.get().getToasterStatus();
                }
                if (toasterStatus == Toaster.ToasterStatus.Up) {
                    if (outOfBread()) {
                        LOG.debug("Toaster is out of bread");
                        return Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException("", makeToasterOutOfBreadError()));
                    } else {
                        LOG.debug("Set Toaster to Down, Making toaster...");
                        tx.put(LogicalDatastoreType.OPERATIONAL, TOASTER_ID, buildToaster(Toaster.ToasterStatus.Down));
                        return tx.submit();
                    }
                } else {
                    LOG.debug("Toaster is making bread...");
                    return Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException("", makeToasterInUseError()));
                }
            }
        });
        Futures.addCallback(commitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                currentMakeToastTask.set(executor.submit(new MakeToastTask(input, futureResult)));
            }
            @Override
            public void onFailure(Throwable t) {
                if (t instanceof OptimisticLockFailedException) {
                    if (tries - 1 > 0) {
                        LOG.debug("Get Optimistic Lock Failed Exception, Try Again");
                        checkStatusAndMakeToast(input, futureResult, tries - 1);
                    } else {
                        futureResult.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION,
                                t.getMessage()).build());
                    }
                } else {
                    LOG.debug("Fail to commit: {}", t);
                    futureResult.set(RpcResultBuilder.<Void>failed().withRpcErrors(((TransactionCommitFailedException)t).getErrorList()).build());
                }
            }
        });
    }

    private boolean outOfBread() {
        return amountOfBreadInStock.get() == 0;
    }

    private Toaster buildToaster(Toaster.ToasterStatus toasterStatus) {
        return new ToasterBuilder().setToasterManufacturer(TOASTER_MANUFACTURER)
                .setToasterModelNumber(TOASTER_MODEL_NUMBER)
                .setToasterStatus(toasterStatus).build();
    }

    private RpcError makeToasterOutOfBreadError() {
        return RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "resource-denied", "Toaster is out of bread");
    }

    private RpcError makeToasterInUseError() {
        return RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "resource-in-use", "Toaster is in use");
    }

    private class MakeToastTask implements Callable<Void> {

        final MakeToastInput toastRequest;
        final SettableFuture<RpcResult<Void>> futureResult;

        public MakeToastTask(MakeToastInput toastRequest, SettableFuture<RpcResult<Void>> futureResult) {
            this.toastRequest = toastRequest;
            this.futureResult = futureResult;
        }

        @Override
        public Void call() throws Exception {
            try {
                Thread.sleep(darknessFactor.get() * toastRequest.getToasterDoneness());
            } catch (InterruptedException e) {
                LOG.info("Interrupted when making the toast");
            }
            toastsMade.incrementAndGet();
            amountOfBreadInStock.getAndDecrement();
            if (outOfBread()) {
                LOG.info("Toaster is running out of bread");
                notificationService.publish(new ToasterOutofBreadBuilder().build());
            }
            setToasterStatusUp(new Function<Boolean, Void>() {
                @Nullable
                @Override
                public Void apply(@Nullable Boolean input) {
                    currentMakeToastTask.set(null);
                    LOG.debug("Toast Done");
                    futureResult.set(RpcResultBuilder.<Void>success().build());
                    return null;
                }
            });
            return null;
        }
    }

    private void setToasterStatusUp(Function<Boolean, Void> resultCallBack) {
        WriteTransaction tx = dataService.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, TOASTER_ID, buildToaster(Toaster.ToasterStatus.Up));
        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                if (resultCallBack != null) {
                    resultCallBack.apply(true);
                }
            }
            @Override
            public void onFailure(Throwable t) {
                if (resultCallBack != null) {
                    resultCallBack.apply(false);
                }
            }
        });
    }

    @Override
    public Long getToastsMade() {
        return toastsMade.get();
    }

    @Override
    public void clearToastsMade() {
        LOG.info("Clear Made Toasts: {}", toastsMade.get());
        toastsMade.set(0);
    }

}
