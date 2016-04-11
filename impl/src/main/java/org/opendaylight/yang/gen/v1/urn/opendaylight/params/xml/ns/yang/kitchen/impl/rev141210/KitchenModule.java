package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kitchen.impl.rev141210;

import org.opendaylight.toaster.impl.KitchenProvider;
import org.opendaylight.toaster.impl.ToasterProvider;

public class KitchenModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kitchen.impl.rev141210.AbstractKitchenModule {
    public KitchenModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public KitchenModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kitchen.impl.rev141210.KitchenModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        ToasterProvider toasterProvider = new ToasterProvider();
        getBrokerDependency().registerProvider(toasterProvider);
        KitchenProvider provider = new KitchenProvider(toasterProvider);
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
