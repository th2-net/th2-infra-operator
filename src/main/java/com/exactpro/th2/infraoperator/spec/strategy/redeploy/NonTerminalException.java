package com.exactpro.th2.infraoperator.spec.strategy.redeploy;

public class NonTerminalException extends RuntimeException {

    public NonTerminalException(String message) {
        super(message);
    }

    public NonTerminalException(String message, Throwable cause) {
        super(message, cause);
    }
}
