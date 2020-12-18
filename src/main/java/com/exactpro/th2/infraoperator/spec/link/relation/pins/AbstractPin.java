package com.exactpro.th2.infraoperator.spec.link.relation.pins;

abstract class AbstractPin implements Pin {

    private String boxName;
    private String pinName;

    protected AbstractPin(String boxName, String pinName) {
        this.boxName = boxName;
        this.pinName = pinName;
    }

    public String getBoxName() {
        return this.boxName;
    }

    public String getPinName() {
        return this.pinName;
    }

    public boolean equals(final Object o) {
        throw new AssertionError("method not defined");
    }

    public int hashCode() {
        throw new AssertionError("method not defined");
    }

    @Override
    public String toString() {
        return String.format("%s(%s:%s)", this.getClass().getName(), getBoxName(), getPinName());
    }

}
