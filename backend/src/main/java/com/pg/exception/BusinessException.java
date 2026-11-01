package com.pg.exception;

/**
 * A rule of the application was broken (room full, already paid, invalid status change...).
 * The message is written for the user and IS shown to the client (HTTP 400).
 * Any OTHER exception is treated as a server bug: logged, and the client only sees a generic message.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
