package ua.zhenya.cloudstorage.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.config.UserDetailsImpl;
import ua.zhenya.cloudstorage.dto.ResourceDownloadResponse;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.service.impl.ResourceServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resource")
@Validated
@Slf4j
public class ResourceController {
    private final ResourceServiceImpl resourceService;

    @GetMapping
    public ResponseEntity<ResourceResponse> getResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                        @RequestParam @NotBlank(message = "'path' must not be blank") String path) {
        ResourceResponse resourceInfo = resourceService.getResourceInfo(userDetailsImpl.getId(), path);
        log.info("Received GET /api/resource request for user ID: {} and path: '{}'", userDetailsImpl.getId(), path);
        return new ResponseEntity<>(resourceInfo, HttpStatus.OK);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ResourceResponse>> uploadResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                 @RequestParam String path,
                                                                 @RequestPart("object") List<MultipartFile> files) {
        Integer userId = userDetailsImpl.getId();
        int fileCount = (files != null) ? files.size() : 0;
        log.info("Received POST /api/resource request for user ID: {} to path: '{}' with {} file(s)", userId, path, fileCount);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(userDetailsImpl.getId(), path, files);
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(resourceResponses);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                            @RequestParam @NotBlank(message = "'path' must not be blank") String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received DELETE /api/resource request for user ID: {} and path: '{}'", userId, path);
        resourceService.deleteResource(userDetailsImpl.getId(), path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                     @RequestParam @NotBlank(message = "'path' must not be blank") String path) {
        ResourceDownloadResponse resource = resourceService.downloadResource(userDetailsImpl.getId(), path);
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/resource/download request for user ID: {} and path: '{}'", userId, path);
        String fileName = resource.getFileName();

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource.getContent());
    }

    @GetMapping("/move")
    public ResponseEntity<ResourceResponse> moveResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                         @RequestParam @NotBlank(message = "'from' must not be blank") String from,
                                                         @RequestParam @NotBlank(message = "'to' must not be blank") String to) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/resource/move request for user ID: {} from path: '{}' to path: '{}'", userId, from, to);
        ResourceResponse response = resourceService.moveResource(userDetailsImpl.getId(), from, to);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ResourceResponse>> searchResource(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                 @RequestParam @NotBlank(message = "'query' must not be blank") String query) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/resource/search request for user ID: {} with query: '{}'", userId, query);
        return ResponseEntity.ok(resourceService.searchResources(userDetailsImpl.getId(), query));
    }
}
