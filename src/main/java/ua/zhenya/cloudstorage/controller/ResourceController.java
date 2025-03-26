package ua.zhenya.cloudstorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Resource Management", description = "API for managing user files and folders in cloud storage")
public class ResourceController {
    private final ResourceServiceImpl resourceService;

    @Operation(summary = "Get resource information", description = "Returns information about a file or folder at the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource information retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., blank 'path')",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Resource not found at the specified path",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    @GetMapping
    public ResponseEntity<ResourceResponse> getResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                        @RequestParam @NotBlank(message = "'path' must not be blank") String path) {
        ResourceResponse resourceInfo = resourceService.getResourceInfo(userDetailsImpl.getId(), path);
        log.info("Received GET /api/resource request for user ID: {} and path: '{}'", userDetailsImpl.getId(), path);
        return new ResponseEntity<>(resourceInfo, HttpStatus.OK);
    }

    @Operation(summary = "Upload files", description = "Uploads one or more files to the specified user folder. If the path is empty, uploads to the user's root folder.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Files uploaded successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "array", implementation = ResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., incorrect 'path' or missing files)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Target directory not found",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict: A resource with the same name already exists in the target directory",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ResourceResponse>> uploadResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                 @RequestParam String path,
                                                                 @RequestPart("object") List<MultipartFile> files) {
        Integer userId = userDetailsImpl.getId();
        int fileCount = (files != null) ? files.size() : 0;
        log.info("Received POST /api/resource request for user ID: {} to path: '{}' with {} file(s)", userId, path, fileCount);
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(userDetailsImpl.getId(), path, files);
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(resourceResponses);
    }

    @Operation(summary = "Delete resource", description = "Deletes a file or a folder (including all its content) at the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Resource deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., blank 'path')",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Resource not found at the specified path",
                    content = @Content)
    })
    @DeleteMapping
    public ResponseEntity<?> deleteResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                            @RequestParam @NotBlank(message = "'path' must not be blank") String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received DELETE /api/resource request for user ID: {} and path: '{}'", userId, path);
        resourceService.deleteResource(userDetailsImpl.getId(), path);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Download resource", description = "Downloads a file or a folder (as a zip archive) from the specified path.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File or archive sent successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., blank 'path')",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Resource not found at the specified path",
                    content = @Content)
    })
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
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

    @Operation(summary = "Move/Rename resource", description = "Moves or renames a file or folder.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resource moved/renamed successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., blank paths, invalid target path type)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Source resource not found",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict: A resource with the same name already exists at the target path",
                    content = @Content)
    })
    @GetMapping("/move")
    public ResponseEntity<ResourceResponse> moveResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                         @RequestParam @NotBlank(message = "'from' must not be blank") String from,
                                                         @RequestParam @NotBlank(message = "'to' must not be blank") String to) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/resource/move request for user ID: {} from path: '{}' to path: '{}'", userId, from, to);
        ResourceResponse response = resourceService.moveResource(userDetailsImpl.getId(), from, to);
        return ResponseEntity.ok().body(response);
    }

    @Operation(summary = "Search resources", description = "Searches for files by a part of their name in the user's storage (case-insensitive). Does not search within folders.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully. Returns a list of found files.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "array", implementation = ResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., blank 'query')",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content)
    })
    @GetMapping("/search")
    public ResponseEntity<List<ResourceResponse>> searchResource(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                 @RequestParam @NotBlank(message = "'query' must not be blank") String query) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/resource/search request for user ID: {} with query: '{}'", userId, query);
        return ResponseEntity.ok(resourceService.searchResources(userDetailsImpl.getId(), query));
    }
}
