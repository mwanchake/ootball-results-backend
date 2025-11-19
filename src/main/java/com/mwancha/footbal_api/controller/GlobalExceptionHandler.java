package com.mwancha.footbal_api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        // Log full stacktrace with the error id for later lookup
        log.error("ErrorId={} - Unhandled exception: {}", errorId, ex.toString(), ex);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Server error. Please try again later.");
        body.put("id", errorId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
