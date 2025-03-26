package ua.zhenya.cloudstorage.utils;

import ua.zhenya.cloudstorage.dto.ResourceType;

import java.nio.file.Paths;

import static ua.zhenya.cloudstorage.utils.Constants.USER_DIRECTORY_PATH;

public class PathUtils {
    public static String buildPath(Integer userId, String path) {
        String userDirectory = USER_DIRECTORY_PATH.formatted(userId);

        if (path.equals("/")) {
            return userDirectory;
        }

        return userDirectory + path;
    }

    public static String getRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "/";
        }

        if (isDirectory(absolutePath)) {
            absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
        }

        int lastSlashIndex = absolutePath.lastIndexOf("/");

        if (lastSlashIndex == -1) {
            return absolutePath;
        }

        return ensureTrailingSlash(absolutePath.substring(0, lastSlashIndex));
    }

    public static String ensureTrailingSlash(String path) {
        return isDirectory(path) ? path : path + "/";
    }

    public static String getFileOrFolderName(String path) {
        if (isDirectory(path)) {
            path = path.substring(0, path.length() - 1);
        }

        return Paths.get(path).getFileName().toString();
    }

    public static boolean isDirectory(String absolutePath) {
        return absolutePath.endsWith("/");
    }

    public static String getCorrectResponsePath(String absolutePath) {
        String relativePath = getRelativePath(absolutePath);
        if (relativePath == null || relativePath.isEmpty())
            return "";

        int firstSlashIndex = relativePath.indexOf("/");
        if (firstSlashIndex == -1)
            return "";

        String trimmedPath = relativePath.substring(firstSlashIndex + 1);
        return trimmedPath.isBlank() ? "" : trimmedPath;
    }

    public static ResourceType extractResourceType(String absolutePath) {
        return isDirectory(absolutePath) ? ResourceType.DIRECTORY : ResourceType.FILE;
    }
}
