package com.cmt.e2e.framework.assertion;

public class VerificationFailedException extends AssertionError {
    private final String actual;
    private final String expected;

    public VerificationFailedException(String message, String actual, String expected) {
        super(message);
        this.actual = actual;
        this.expected = expected;
    }

    public VerificationFailedException(String message, String actual, String expected, Throwable cause) {
        super(message, cause);
        this.actual = actual;
        this.expected = expected;
    }

    public String getActual() {
        return actual;
    }

    public String getExpected() {
        return expected;
    }
}
