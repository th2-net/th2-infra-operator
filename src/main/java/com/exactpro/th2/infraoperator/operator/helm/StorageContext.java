package com.exactpro.th2.infraoperator.operator.helm;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Getter
@SuperBuilder
public abstract class StorageContext {

    private String linkResourceName;

    private String linkNameSuffix;

    private String boxAlias;

    private String pinName;

    public abstract boolean checkAttributes(Set<String> attributes);

}
