/*
 * Copyright © 2015 fdsa and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.toaster.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.*;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.common.util.Rpcs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kitchen.rev150105.KitchenService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kitchen.rev150105.MakeBreakfastInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.MakeToastInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.MakeToastInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.toaster.rev150105.ToasterService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KitchenProvider implements BindingAwareProvider, KitchenService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KitchenProvider.class);

    private ToasterService toasterService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public KitchenProvider(ToasterService toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("ToasterProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("ToasterProvider Closed");
    }

    @Override
    public Future<RpcResult<Void>> makeBreakfast(MakeBreakfastInput input) {
        ListenableFuture<RpcResult<Void>> makeEggFuture = makeEgg(input);
        ListenableFuture<RpcResult<Void>> makeToastFuture = makeToast(input);
        ListenableFuture<List<RpcResult<Void>>> combinedFutures = Futures.allAsList(ImmutableList.of(makeEggFuture, makeToastFuture));
        return Futures.transform(combinedFutures, new AsyncFunction<List<RpcResult<Void>>, RpcResult<Void>>() {
            @Override
            public ListenableFuture<RpcResult<Void>> apply(List<RpcResult<Void>> input) throws Exception {
                boolean atLeastOneSucceed = false;
                ImmutableList.Builder<RpcError> errorList = ImmutableList.builder();
                for (RpcResult<Void> result : input) {
                    if (result.isSuccessful()) {
                        atLeastOneSucceed = true;
                    }
                    if (result.getErrors() != null) {
                        errorList.addAll(result.getErrors());
                    }
                }
                return Futures.immediateFuture(Rpcs.<Void>getRpcResult(atLeastOneSucceed, errorList.build()));
            }
        });
    }

    private ListenableFuture<RpcResult<Void>> makeEgg(final MakeBreakfastInput input) {
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        executor.submit(new MakeEggTask(input, futureResult));
        return futureResult;
    }

    private  class MakeEggTask implements Callable<Void> {

        final MakeBreakfastInput breakfastRequest;
        final SettableFuture<RpcResult<Void>> futureResult;

        public MakeEggTask(MakeBreakfastInput breakfastRequest, SettableFuture<RpcResult<Void>> futureResult) {
            this.breakfastRequest = breakfastRequest;
            this.futureResult = futureResult;
        }

        @Override
        public Void call() throws Exception {
            try {
                LOG.info("Making Egg, Sleep for 5 Seconds");
                Thread.sleep(5000);
                futureResult.set(RpcResultBuilder.<Void>success().build());
            } catch (InterruptedException e) {
                LOG.info("Interrupted when making the Egg: {}", e);
                futureResult.set(RpcResultBuilder.<Void>failed().withError(RpcError.ErrorType.APPLICATION,
                        e.getMessage()).build());
            }
            return null;
        }
    }

    private ListenableFuture<RpcResult<Void>> makeToast(final MakeBreakfastInput input) {
        MakeToastInput toastInput = new MakeToastInputBuilder()
                .setToasterToastType(input.getToasterToastType())
                .setToasterDoneness(input.getToasterDoneness()).build();
        return JdkFutureAdapters.listenInPoolThread(toasterService.makeToast(toastInput));
    }

}
