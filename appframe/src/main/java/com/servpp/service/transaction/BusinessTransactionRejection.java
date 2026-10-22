package com.servpp.service.transaction;

/** Framework rollback signal; never exposed as an SPP exception construct. */
public class BusinessTransactionRejection extends RuntimeException {
    public BusinessTransactionRejection(String code) { super(code); }
}
