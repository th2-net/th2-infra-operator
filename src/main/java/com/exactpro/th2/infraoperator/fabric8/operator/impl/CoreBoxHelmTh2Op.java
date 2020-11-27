package com.exactpro.th2.infraoperator.fabric8.operator.impl;

import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.CoreBoxClient;
import com.exactpro.th2.infraoperator.fabric8.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.corebox.Th2CoreBox;
import io.fabric8.kubernetes.client.KubernetesClient;

public class CoreBoxHelmTh2Op extends GenericHelmTh2Op<Th2CoreBox> {

    private final CoreBoxClient coreBoxClient;


    public CoreBoxHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.coreBoxClient = new CoreBoxClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2CoreBox> getResourceClient() {
        return coreBoxClient;
    }


    @Override
    protected String getKubObjDefPath(Th2CoreBox resource) {
        return "/cr/helm/th2-core-box-helm-release-live.yml";
    }


    public static CoreBoxHelmTh2Op.Builder builder(KubernetesClient client) {
        return new CoreBoxHelmTh2Op.Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<CoreBoxHelmTh2Op, CoreBoxHelmTh2Op.Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public CoreBoxHelmTh2Op build() {
            return new CoreBoxHelmTh2Op(this);
        }

        @Override
        protected CoreBoxHelmTh2Op.Builder self() {
            return this;
        }

    }

}
