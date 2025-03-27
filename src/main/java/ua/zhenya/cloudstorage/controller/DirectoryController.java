package ua.zhenya.cloudstorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ua.zhenya.cloudstorage.config.UserDetailsImpl;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.service.ResourceService;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/directory")
@Slf4j
@Validated
@Tag(name = "Directory Management", description = "API for managing user directories in cloud storage")
public class DirectoryController {
    private final ResourceService resourceService;

    @Operation(summary = "Create a new directory", description = "Creates a directory at the specified path. The path must end with a '/'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Directory created successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ResourceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., path is blank, path doesn't end with '/', or parent path is invalid)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Parent directory not found",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict: A directory or file already exists at this path",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<ResourceResponse> createDirectory(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                            @RequestParam @NotBlank String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received POST /api/directory request for user ID: {}, path '{}'", userId, path);
        ResourceResponse response = resourceService.createDirectory(userDetailsImpl.getId(), path);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/resource")
                .queryParam("path", path)
                .build()
                .encode()
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "List directory contents", description = "Retrieves a list of files and subdirectories within the specified directory path. Provide an empty path to list the root directory.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Directory contents retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = ResourceResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., path is provided but doesn't end with '/' and isn't empty)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "User not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Directory not found at the specified path",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error while listing contents",
                    content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<ResourceResponse>> getDirectoryContext(@Parameter(hidden = true) @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                                                                      @RequestParam(defaultValue = "") String path) {
        Integer userId = userDetailsImpl.getId();
        log.info("Received GET /api/directory request for user ID: {}, path '{}'", userId, path);
        return ResponseEntity.ok(resourceService.getDirectoryContent(userDetailsImpl.getId(), path));
    }
}
