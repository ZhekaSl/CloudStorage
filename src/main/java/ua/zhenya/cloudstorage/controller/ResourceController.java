package ua.zhenya.cloudstorage.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.zhenya.cloudstorage.dto.ResourceDownloadResponse;
import ua.zhenya.cloudstorage.dto.ResourceResponse;
import ua.zhenya.cloudstorage.service.impl.ResourceServiceImpl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resource")
public class ResourceController {
    private final ResourceServiceImpl resourceService;

    @GetMapping
    public ResponseEntity<ResourceResponse> getResource(@RequestParam Integer id, @RequestParam String path) {
        ResourceResponse resourceInfo = resourceService.getResourceInfo(id, path);
        return new ResponseEntity<>(resourceInfo, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<List<ResourceResponse>> uploadResource(@RequestParam(value = "userId") Integer id, @RequestParam String path, @RequestParam MultipartFile[] files) {
        List<ResourceResponse> resourceResponses = resourceService.uploadResources(id, path, files);
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(resourceResponses);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteResource(Integer id, @RequestParam String path) {
        resourceService.deleteResource(id, path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadResource(@RequestParam Integer id, @RequestParam String path) {
        ResourceDownloadResponse resource = resourceService.downloadResource(id, path);
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
    public ResponseEntity<ResourceResponse> moveResource(@RequestParam Integer id, @RequestParam String from, @RequestParam String to) {
        ResourceResponse response = resourceService.moveResource(id, from, to);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ResourceResponse>> searchResource(@RequestParam Integer id, @RequestParam String query) {
        return ResponseEntity.ok(resourceService.searchResources(id, query));
    }
}
