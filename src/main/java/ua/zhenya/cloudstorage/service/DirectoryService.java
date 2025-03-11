package ua.zhenya.cloudstorage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.zhenya.cloudstorage.dto.DirectoryResponse;

@Service
@RequiredArgsConstructor
public class DirectoryService {
    private final MinioService minioService;

    public DirectoryResponse createDirectory(Integer userId, String path) {
        String fullPath = "user-" + userId + "-files";

    }
}
