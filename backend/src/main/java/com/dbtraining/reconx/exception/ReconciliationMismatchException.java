package com.dbtraining.reconx.exception;

/** 422 Unprocessable: internal vs external trade do not match. */
public class ReconciliationMismatchException extends ReconException {
    public ReconciliationMismatchException(String message) { super(message); }
}
