package ua.zhenya.cloudstorage.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CloudStorageException.class)
    public ResponseEntity<Object> handleCloudStorageException(CloudStorageException ex, NativeWebRequest request) {

        HttpStatus status = ex.getStatus();
        HttpHeaders headers = new HttpHeaders();
        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        List<MediaType> acceptedMediaTypes = MediaType.parseMediaTypes(acceptHeader == null ? "*/*" : acceptHeader);

        boolean clientAcceptsJsonOrAll = acceptedMediaTypes.stream()
                .anyMatch(mt -> mt.isCompatibleWith(MediaType.APPLICATION_JSON) || mt.isCompatibleWith(MediaType.ALL));

        if (clientAcceptsJsonOrAll) {
            ErrorResponse body = new ErrorResponse(ex.getMessage());
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(body, headers, status);
        } else {
            return ResponseEntity.status(status).build();
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = "Validation failed: " + ex.getBindingResult().getFieldError().getDefaultMessage();
        ErrorResponse errorResponse = new ErrorResponse(errorMessage);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        String errorMessage = "Validation failed: " + ex.getConstraintViolations().iterator().next().getMessage();
        ErrorResponse errorResponse = new ErrorResponse(errorMessage);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException() {
        ErrorResponse errorResponse = new ErrorResponse("Authentication required!");
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
