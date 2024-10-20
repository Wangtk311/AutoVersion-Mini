package com.github.wangtk311.plugintest.services;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.patch.AbstractDelta;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileChange implements Serializable {  // 实现 Serializable 接口
    @Serial
    private static final long serialVersionUID = 1L;

    public enum ChangeType {
        ADD, DELETE, MODIFY
    }

    private String filePath;
    private Patch<String> EachFilePatch; // 如果文件是新增或修改的，保存其内容
    private ChangeType changeType;
    //private Patch<String> patch; // 如果文件是修改的，保存其差异


    public FileChange(String filePath, Patch<String> EachFilePatch, ChangeType changeType) {
        this.filePath = filePath;
        this.EachFilePatch = EachFilePatch;
        this.changeType = changeType;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<Map<String, FileChange>> getProjectVersions() {
        //File versionFile = new File(VersionStorage.VERSION_STORAGE_FILE);
        List<Map<String, FileChange>>  projectVersions=VersionStorage.getProjectVersions();

//        Map<String, FileChange> versionContents = projectVersions.get(0);
//        for (Map.Entry<String, FileChange> entry : versionContents.entrySet()) {
//            FileChange fileChange = entry.getValue();
//            System.out.println("----------------------------------------------\n");
//            System.out.println("文件: " + fileChange.getFilePath() + "\n");
//            System.out.println("操作: " + fileChange.getChangeType() + "\n");
//            System.out.println("内容:\n\n[===文件开始===]\n" + (fileChange.getEachFilePatch().toString() == null ? "[文件无内容]" : fileChange.getEachFilePatch().toString()) + "\n[===文件结束===]\n\n");//-----------------------------------------------------------------------------------
//        }

//        int versionIndex = 1;
//        // 遍历每个版本（List中的Map）
//        for (Map<String, FileChange> version : projectVersions) {
//            System.out.println("Version " + versionIndex + ":");
//            versionIndex++;
//
//            // 遍历每个版本中的文件变更
////            version.forEach( (key, fileChange) -> {
////                System.out.println("  File Path: " + fileChange.getFilePath());
////                System.out.println("  Change Type: " + fileChange.getChangeType());
////                System.out.println("  Patch: \n" + fileChange.getEachFilePatch().toString());
////                System.out.println("--------------------------------");
////            });
//        }
        return projectVersions;
    }


    public List<Patch<String>> getEachFilePatches(List<Map<String, FileChange>> listOfMaps, String filePath,int end) {
        List<Patch<String>> patches = new ArrayList<>();
        System.out.println("getEachFilePatches---listOfMaps: \n" + listOfMaps);
        int i=0;
        int x=0;
        for (Map<String, FileChange> map : listOfMaps) {

            FileChange fileChange = map.get(filePath);
            if (fileChange != null) {

                //System.out.println("\nfileChange---patch:\n" + fileChange.getEachFilePatch());
                patches.add(fileChange.getEachFilePatch());
                x++;
            }
            System.out.println("\nfilePath:\n" + filePath);
            //System.out.println("\nfileChange---Path:\n" + fileChange.getFilePath());
            System.out.println("Version-------" + i);
            System.out.println("\nPatch-------" + x);
            if(i==end)
                break;
            i++;
        }

        return patches;
    }
    public Patch<String> getEachFilePatch() {

        return EachFilePatch;
    }


    private static void printPatchDetails(Patch<String> patch) {
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            System.out.println("Delta Type: " + delta.getType());
            System.out.println("Source Position: " + delta.getSource().getPosition());
            System.out.println("Source Lines: " + delta.getSource().getLines());
            System.out.println("Target Position: " + delta.getTarget().getPosition());
            System.out.println("Target Lines: " + delta.getTarget().getLines());
            System.out.println("=================================");
        }
    }



    public static List<String> applyPatches( List<Patch<String>> patches)  {
        List<String> currentContent = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++) {
            Patch<String> patch = patches.get(i);
            System.out.println("Patch " + (i + 1) + ":");
            printPatchDetails(patch);
            System.out.println("-----------------------------");
        }
        for (Patch<String> patch : patches) {
            try {
                //System.out.println("Applying patch:\n" + patch);
                // 应用每个补丁并更新 currentContent
                currentContent = patch.applyTo(currentContent);
                System.out.println("currentContent---------------------------:\n" + currentContent);
            } catch (PatchFailedException e) {
                System.err.println("Patch failed to apply: " + e.getMessage());
                e.printStackTrace();
                break;  // 如果应用失败，可以选择中断或继续处理其他补丁
            }
        }

        return currentContent;
    }
    public String getFileContent(int end)  {
        System.out.println("getFileContent---filePath: \n" + filePath);
        List<Patch<String>> patches = getEachFilePatches(getProjectVersions(), filePath,end);
        for (Patch<String> patch : patches) {
            System.out.println("getFileContent-----------------patch: \n" + patch.toString());
        }
        //System.out.println("getFileContent---patches: \n" + patches);
        List<String> fileContent = applyPatches(patches);
        String FileContent = String.join("\n", fileContent);

        return FileContent;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
