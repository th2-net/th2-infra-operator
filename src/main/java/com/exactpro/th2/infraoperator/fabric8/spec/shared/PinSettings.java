package com.exactpro.th2.infraoperator.fabric8.spec.shared;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
@JsonDeserialize
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PinSettings {

    protected String storageOnDemand = "true";

    protected String queueLength = "1000";

}
