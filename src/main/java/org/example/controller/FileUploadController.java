package org.example.controller;

import org.example.config.FileUploadConfig;
import org.example.domain.vo.ApiResponse;
import org.example.domain.vo.FileUploadRes;
import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传控制器。
 *
 * <p>负责接收前端上传的文档文件、保存到配置目录，并在上传成功后同步触发文件向量化入库流程。
 * 该控制器是文档接入链路的入口之一，连接文件存储与 Milvus 向量化入库服务。</p>
 */
@RestController
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private VectorIndexService vectorIndexService;

    /**
     * 文件上传并同步触发向量化入库。
     *
     * <p>流程：
     * <ol>
     *   <li>校验文件与后缀</li>
     *   <li>保存到配置目录（覆盖同名文件）</li>
     *   <li>同步触发该文件的向量化入库（入库失败时仅记录日志，不回滚上传）</li>
     * </ol>
     * </p>
     *
     * @param file Multipart 文件参数
     * @return 上传结果的 ApiResponse，包含文件元信息或错误信息
     */
    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return ResponseEntity.badRequest().body("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions());
        }

        try {
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(originalFilename).normalize();
            if (Files.exists(filePath)) {
                logger.info("文件已存在，将覆盖: {}", filePath);
                Files.delete(filePath);
            }

            Files.copy(file.getInputStream(), filePath);
            logger.info("文件上传成功: {}", filePath);

            try {
                logger.info("开始执行上传文件的向量化入库: {}", filePath);
                // 注意：向量化入库可能耗时，当前为同步调用。若并发或大文件频繁上传，建议改为异步任务队列。
                vectorIndexService.indexSingleFile(filePath.toString());
                logger.info("上传文件向量化入库成功: {}", filePath);
            } catch (Exception e) {
                // 向量化入库失败不影响文件上传，记录错误便于人工排查
                logger.error("上传文件向量化入库失败: {}, 错误: {}", filePath, e.getMessage(), e);
            }

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文件扩展名（不含点），若无扩展名返回空字符串。
     */
    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return filename.substring(lastIndexOf + 1).toLowerCase();
    }

    /**
     * 校验扩展名是否在允许列表中。
     */
    private boolean isAllowedExtension(String extension) {
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return false;
        }
        List<String> allowedList = Arrays.asList(allowedExtensions.split(","));
        return allowedList.contains(extension.toLowerCase());
    }
}
