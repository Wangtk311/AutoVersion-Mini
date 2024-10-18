package com.github.wangtk311.plugintest.services;

import java.io.Serial;
import java.io.Serializable;

public class FileChange implements Serializable {  // 实现 Serializable 接口
    @Serial
    private static final long serialVersionUID = 1L;

    public enum ChangeType {
        ADD, DELETE, MODIFY
    }

    private String filePath;
    private String fileContent; // 如果文件是新增或修改的，保存其内容
    private ChangeType changeType;

    public FileChange(String filePath, String fileContent, ChangeType changeType) {
        this.filePath = filePath;
        this.fileContent = fileContent;
        this.changeType = changeType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileContent() {
        return fileContent;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
