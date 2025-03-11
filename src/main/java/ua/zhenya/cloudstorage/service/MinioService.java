package ua.zhenya.cloudstorage.service;

public interface MinioService {
    void createDirectory(String fullPath);
    void createBucket();
}
