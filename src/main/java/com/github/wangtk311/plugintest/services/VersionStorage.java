package com.github.wangtk311.plugintest.services;

import com.github.weisj.jsvg.F;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class VersionStorage {
    public static String VERSION_STORAGE_FILE = "autoversion.record.bin";  // 文件名，保存版本数据
    public static String VERSION_MAP_FILE = "autoversion.map.bin";  // 文件名，保存版本映射数据
    private static List<Map<String, FileChange>> projectVersions = new ArrayList<>();
    public static Map<Integer, Integer> majorToMinorVersionMap;

    // 初始化时从磁盘加载版本历史
    static {
        loadVersionsFromDisk();
    }

    // 获取项目的所有历史版本
    public static List<Map<String, FileChange>> getProjectVersions() {
        return projectVersions;
    }

    // 获取某个版本的内容
    public static Map<String, FileChange> getVersion(int versionIndex) {
        return projectVersions.get(versionIndex);
    }

    // 保存当前项目的文件变化，并保存到磁盘
    public static void saveVersion(Map<String, FileChange> fileChanges) {
        projectVersions.add(new HashMap<>(fileChanges)); // 保存文件变化的副本
        saveVersionsToDisk(); // 保存到磁盘
    }

    // 还原文件到目录
    public static void restoreFileToDirectory(String filePath, String fileContent) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent()); // 如果目录不存在，先创建
            // 如果文件也不存在，则需要先创建文件
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            Files.write(path, fileContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 删除指定路径的文件
    public static void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 保存所有版本到磁盘
    public static void saveVersionsToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(VERSION_STORAGE_FILE))) {
            oos.writeObject(projectVersions);
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveMapToDisk();
    }

    // 从磁盘加载版本
    public static void loadVersionsFromDisk() {
        File versionFile = new File(VERSION_STORAGE_FILE);
        if (versionFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(versionFile))) {
                projectVersions = (List<Map<String, FileChange>>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        loadMapFromDisk();
    }

    public static void clearVersions() {
        projectVersions.clear();
        saveVersionsToDisk();
    }

    public static void loadMapFromDisk() {
        File versionMapFile = new File(VERSION_MAP_FILE);
        if (versionMapFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(versionMapFile))) {
                majorToMinorVersionMap = (Map<Integer, Integer>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        else{
            majorToMinorVersionMap = new HashMap<>(){{
                put(1, 0);
            }};
            saveMapToDisk();
        }
    }

    public static void saveMapToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(VERSION_MAP_FILE))) {
            oos.writeObject(majorToMinorVersionMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
