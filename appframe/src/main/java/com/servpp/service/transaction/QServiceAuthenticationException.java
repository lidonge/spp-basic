package com.servpp.service.transaction;

/**
 * The QService boundary refused the caller (HTTP 401 or 403), or the client holds no usable token.
 * It is not a transport failure: the command was not processed and a retry with the same credentials cannot succeed,
 * so a settlement does not turn it into an UNKNOWN outcome.
 */
public class QServiceAuthenticationException extends IllegalStateException {
    private final int status;

    public QServiceAuthenticationException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** The HTTP status, or 0 when the failure is local. */
    public int status() {
        return status;
    }
}
