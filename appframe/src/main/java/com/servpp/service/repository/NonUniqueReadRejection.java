package com.servpp.service.repository;

import com.servpp.service.transaction.BusinessTransactionRejection;

/** A duplicate keyed read rejects the enclosing scenario across nested boundaries. */
public final class NonUniqueReadRejection extends BusinessTransactionRejection {
    public NonUniqueReadRejection(String code) { super(code); }
}
