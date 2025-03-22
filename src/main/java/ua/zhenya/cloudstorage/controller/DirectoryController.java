package ua.zhenya.cloudstorage.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.service.ResourceService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/directory")
public class DirectoryController {
    private final ResourceService resourceService;

    @PostMapping
    public ResponseEntity<ResourceResponse> createDirectory(@RequestParam Integer id, @RequestParam @NotBlank String path) {
        ResourceResponse response = resourceService.createDirectory(id, path);
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> getDirectoryContext(@RequestParam Integer id, @RequestParam @NotBlank String path) {
        return ResponseEntity.ok(resourceService.getDirectoryContent(id, path));
    }


}
