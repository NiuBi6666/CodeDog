package cn.codedog.controller;

import cn.codedog.service.DocumentService;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DocumentService.NotFoundException.class)
    ResponseEntity<?> notFound(DocumentService.NotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(DocumentService.ValidationException.class)
    ResponseEntity<?> invalid(DocumentService.ValidationException error) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(DocumentService.VersionConflictException.class)
    ResponseEntity<?> conflict(DocumentService.VersionConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "error", error.getMessage(), "document", DocumentDto.from(error.getCurrent(), true)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<?> optimistic(ObjectOptimisticLockingFailureException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "文档已在其他页面更新"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<?> badRequest(Exception error) {
        String message = error instanceof MethodArgumentNotValidException validation && validation.getBindingResult().getFieldError() != null
            ? validation.getBindingResult().getFieldError().getDefaultMessage() : "请求参数无效";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason() == null ? "请求失败" : error.getReason()));
    }
}
