package ua.zhenya.cloudstorage.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CloudStorageException extends RuntimeException{
    private final HttpStatus status;

    public CloudStorageException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
