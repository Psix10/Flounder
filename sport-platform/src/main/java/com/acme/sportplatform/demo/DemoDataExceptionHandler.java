package com.acme.sportplatform.demo;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.acme.sportplatform.common.DemoDataException;
import com.acme.sportplatform.common.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = DemoDataController.class)
@org.springframework.core.annotation.Order(-10)
public class DemoDataExceptionHandler {
    @ExceptionHandler(DemoDataException.class)
    public ResponseEntity<ApiErrorResponse> handle(DemoDataException error) {
        return ResponseEntity.status(error.status()).body(new ApiErrorResponse(
                error.code(), error.getMessage(), OffsetDateTime.now(), List.of()));
    }
}
