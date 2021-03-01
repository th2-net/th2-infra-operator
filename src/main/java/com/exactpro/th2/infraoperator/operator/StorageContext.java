package com.exactpro.th2.infraoperator.operator;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Getter
@SuperBuilder
abstract class StorageContext {

    private String linkResourceName;

    private String linkNameSuffix;

    private String boxAlias;

    private String pinName;

    protected abstract boolean checkAttributes(Set<String> attributes);

}
