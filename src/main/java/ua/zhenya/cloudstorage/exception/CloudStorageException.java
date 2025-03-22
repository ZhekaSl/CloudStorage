package ua.zhenya.cloudstorage.exception;

import org.springframework.http.HttpStatus;

public class CloudStorageException extends RuntimeException{
    private final HttpStatus status;

    public CloudStorageException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
