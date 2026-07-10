package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件上传结果对象。
 *
 * <p>用于返回上传后的文件元信息，通常包含原始文件名、实际存储路径和文件大小。</p>
 */
@Getter
@Setter
public class FileUploadRes {

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 文件实际保存路径。
     */
    private String filePath;

    /**
     * 文件大小，单位字节。
     */
    private Long fileSize;

    /**
     * 无参构造函数。
     */
    public FileUploadRes() {
    }

    /**
     * 创建文件上传结果。
     *
     * @param fileName 文件名
     * @param filePath 存储路径
     * @param fileSize 文件大小（字节）
     */
    public FileUploadRes(String fileName, String filePath, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }
}
