package org.spon.edolams.controller;

import org.spon.edolams.service.UnmappedAmsPrinterException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AmsApiExceptionHandler {

    @ExceptionHandler(UnmappedAmsPrinterException.class)
    ResponseEntity<Void> handleUnmappedPrinter(UnmappedAmsPrinterException exception) {
        return ResponseEntity.notFound().build();
    }
}
