package ua.zhenya.cloudstorage.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.zhenya.cloudstorage.dto.DirectoryResponse;
import ua.zhenya.cloudstorage.service.DirectoryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/directory")
public class DirectoryController {
    private final DirectoryService directoryService;

    @PostMapping
    public ResponseEntity<DirectoryResponse> createDirectory(Long id, @RequestParam String path) {


    }
}
