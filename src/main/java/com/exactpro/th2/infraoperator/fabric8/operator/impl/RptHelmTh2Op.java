package com.exactpro.th2.infraoperator.fabric8.operator.impl;

import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ResourceClient;
import com.exactpro.th2.infraoperator.fabric8.model.kubernetes.client.ipml.RptClient;
import com.exactpro.th2.infraoperator.fabric8.operator.GenericHelmTh2Op;
import com.exactpro.th2.infraoperator.fabric8.operator.context.HelmOperatorContext;
import com.exactpro.th2.infraoperator.fabric8.spec.rpt.Th2Rpt;
import io.fabric8.kubernetes.client.KubernetesClient;

public class RptHelmTh2Op extends GenericHelmTh2Op<Th2Rpt> {

    private final RptClient rptClient;


    public RptHelmTh2Op(HelmOperatorContext.Builder<?, ?> builder) {
        super(builder);
        this.rptClient = new RptClient(builder.getClient());
    }


    @Override
    public ResourceClient<Th2Rpt> getResourceClient() {
        return rptClient;
    }


    @Override
    protected String getKubObjDefPath(Th2Rpt resource) {
        return "/cr/helm/th2-rpt-helm-release-live.yml";
    }


    public static RptHelmTh2Op.Builder builder(KubernetesClient client) {
        return new RptHelmTh2Op.Builder(client);
    }

    public static class Builder extends HelmOperatorContext.Builder<RptHelmTh2Op, RptHelmTh2Op.Builder> {

        public Builder(KubernetesClient client) {
            super(client);
        }

        @Override
        public RptHelmTh2Op build() {
            return new RptHelmTh2Op(this);
        }

        @Override
        protected RptHelmTh2Op.Builder self() {
            return this;
        }

    }

}
