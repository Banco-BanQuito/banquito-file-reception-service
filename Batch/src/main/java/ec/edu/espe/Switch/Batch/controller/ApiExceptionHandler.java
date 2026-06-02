package ec.edu.espe.Switch.Batch.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import ec.edu.espe.Switch.Batch.exception.DuplicateBatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Parametro requerido faltante: " + e.getParameterName()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Parte multipart requerida faltante: " + e.getRequestPartName()));
    }

    @ExceptionHandler(DuplicateBatchException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateBatchException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
}
