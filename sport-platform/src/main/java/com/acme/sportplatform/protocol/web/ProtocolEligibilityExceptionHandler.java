package com.acme.sportplatform.protocol.web;

import java.time.OffsetDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.common.api.ApiErrorResponse;
import com.acme.sportplatform.protocol.application.ProtocolEligibilityException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProtocolEligibilityExceptionHandler {
    @ExceptionHandler(ProtocolEligibilityException.class)
    public ResponseEntity<ApiErrorResponse> handle(ProtocolEligibilityException exception) {
        var fields=exception.violations().stream().map(v->new ApiErrorResponse.FieldViolation(
                "registrations["+v.registrationId()+"]."+v.field(),v.reason()+": "+v.message())).toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getCode(),exception.getMessage(),OffsetDateTime.now(),fields));
    }
}
