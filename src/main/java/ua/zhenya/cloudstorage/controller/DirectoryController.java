package ua.zhenya.cloudstorage.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ua.zhenya.cloudstorage.config.UserDetailsImpl;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.service.ResourceService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/directory")
@Slf4j
public class DirectoryController {
    private final ResourceService resourceService;

    @PostMapping
    public ResponseEntity<ResourceResponse> createDirectory(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                            @RequestParam @NotBlank String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received POST /api/directory request for user ID: {}, path '{}'", userId, path);

        ResourceResponse response = resourceService.createDirectory(userDetailsImpl.getId(), path);
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> getDirectoryContext(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                      @RequestParam String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/directory request for user ID: {}, path '{}'", userId, path);
        return ResponseEntity.ok(resourceService.getDirectoryContent(userDetailsImpl.getId(), path));
    }
}
