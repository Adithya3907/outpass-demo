package com.psgitech.outpass.service;

/** A user-correctable rule violation. The message is shown to the user as-is. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
