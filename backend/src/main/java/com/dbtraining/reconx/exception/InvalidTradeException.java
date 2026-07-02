package com.dbtraining.reconx.exception;

/** 400 Bad Request: a trade failed business validation. */
public class InvalidTradeException extends ReconException {
    public InvalidTradeException(String message) { super(message); }
}
