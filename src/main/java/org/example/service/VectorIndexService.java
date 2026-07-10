package org.example.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import org.example.constant.MilvusConstants;
import org.example.domain.po.DocumentChunk;
import org.example.domain.po.DocumentMetadata;
import org.example.domain.vo.IndexingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档向量化入库服务。
 *
 * <p>负责从文件系统读取文档、调用分片服务进行切分、调用嵌入服务生成向量，并将分片文本、向量和元数据作为向量记录写入 Milvus。
 * 提供按目录批量向量化入库与单文件向量化入库接口，并包含删除旧向量记录、加载 collection 等 Milvus 交互细节处理。</p>
 */
@Service
public class VectorIndexService {

    private static final Logger logger = LoggerFactory.getLogger(VectorIndexService.class);

    /**
     * Milvus 客户端，负责与 Milvus Server 交互（搜索/插入/删除/加载等）。
     */
    @Autowired
    private MilvusServiceClient milvusClient;

    /**
     * 嵌入服务，用于将文档分片内容转换为向量。
     */
    @Autowired
    private VectorEmbeddingService embeddingService;

    /**
     * 文档分片服务，负责根据配置的分片大小和重叠策略将文本切分为多个可向量化并写入的文档分片。
     */
    @Autowired
    private DocumentChunkService chunkService;

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 为指定目录下的所有文件执行向量化入库。
     * 
     * @param directoryPath 目录路径（可选，默认使用配置的上传目录）
     * @return 向量化入库结果；后续可优化为定时重建目录下所有文件的向量记录
     */
    public IndexingResult indexDirectory(String directoryPath) {
        IndexingResult result = new IndexingResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // 使用指定目录或默认上传目录
            String targetPath = (directoryPath != null && !directoryPath.trim().isEmpty()) 
                    ? directoryPath : uploadPath;
                    
            Path dirPath = Paths.get(targetPath).normalize();
            File directory = dirPath.toFile();
            
            if (!directory.exists() || !directory.isDirectory()) {
                throw new IllegalArgumentException("目录不存在或不是有效目录: " + targetPath);
            }

            result.setDirectoryPath(directory.getAbsolutePath());

            // 获取所有支持的文件
            File[] files = directory.listFiles((dir, name) -> 
                name.endsWith(".txt") || name.endsWith(".md")
            );

            if (files == null || files.length == 0) {
                logger.warn("目录中没有找到支持的文件: {}", targetPath);
                result.setTotalFiles(0);
                result.setSuccess(true);
                result.setEndTime(LocalDateTime.now());
                return result;
            }

            result.setTotalFiles(files.length);
            logger.info("开始为目录执行向量化入库: {}, 找到 {} 个文件", targetPath, files.length);

            // 遍历并为每个文件执行向量化入库
            for (File file : files) {
                try {
                    indexSingleFile(file.getAbsolutePath());
                    result.incrementSuccessCount();
                    logger.info("✓ 文件向量化入库成功: {}", file.getName());
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.getAbsolutePath(), e.getMessage());
                    logger.error("✗ 文件向量化入库失败: {}", file.getName(), e);
                }
            }

            result.setSuccess(result.getFailCount() == 0);
            result.setEndTime(LocalDateTime.now());

            logger.info("目录向量化入库完成: 总数={}, 成功={}, 失败={}",
                result.getTotalFiles(), result.getSuccessCount(), result.getFailCount());

            return result;

        } catch (Exception e) {
            logger.error("目录向量化入库失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        }
    }

    /**
     * 为单个文件执行向量化入库。
     * 
     * @param filePath 文件路径
     * @throws Exception 向量化入库失败时抛出异常
     */
    public void indexSingleFile(String filePath) throws Exception {
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();
        
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        logger.info("开始为文件执行向量化入库: {}", path);

        // 1. 读取文件内容
        String content = Files.readString(path);
        logger.info("读取文件: {}, 内容长度: {} 字符", path, content.length());

        // 2. 删除该文件的旧向量记录（如果存在），避免重复写入
        deleteExistingData(path.toString());

        // 3. 文档分片
        List<DocumentChunk> chunks = chunkService.chunkDocument(content, path.toString());
        logger.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());

        // 4. 为每个分片生成向量并插入 Milvus
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            
            try {
                // 生成向量
                List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());

                // 构建元数据（包含文件信息）
                DocumentMetadata metadata = buildMetadata(path.toString(), chunk, chunks.size());

                // 插入到 Milvus
                insertToMilvus(chunk.getContent(), vector, metadata, chunk.getChunkIndex());
                
                logger.info("✓ 分片 {}/{} 向量记录写入成功", i + 1, chunks.size());

            } catch (Exception e) {
                logger.error("✗ 分片 {}/{} 向量化入库失败", i + 1, chunks.size(), e);
                throw new RuntimeException("分片向量化入库失败: " + e.getMessage(), e);
            }
        }

        logger.info("文件向量化入库完成: {}, 共 {} 个分片", filePath, chunks.size());
    }

    /**
     * 删除文件的旧向量记录（根据 metadata._source）。
     */
    private void deleteExistingData(String filePath) {
        try {
            // 使用统一的路径分隔符（正斜杠）用于Milvus存储，避免表达式解析错误
            // 将系统路径转换为统一格式
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace(File.separator, "/");
            
            // 构建删除表达式：metadata["_source"] == "xxx"
            // 注意：Milvus 过滤表达式语法较为严格，metadata 字段为 JSON 对象时通过 metadata["_source"] 引用。
            String expr = String.format("metadata[\"_source\"] == \"%s\"", normalizedPath);
            
            logger.info("准备删除旧向量记录，路径: {}, 表达式: {}", normalizedPath, expr);

            // 确保 collection 已加载（删除操作需要集合已加载）
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build()
            );

            // 状态码 65535 表示集合已经加载，这不是错误
            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                logger.warn("加载 collection 失败: {}", loadResponse.getMessage());
                return;
            }

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            R<MutationResult> response = milvusClient.delete(deleteParam);

            if (response.getStatus() != 0) {
                logger.warn("删除旧向量记录时出现警告: {}", response.getMessage());
            } else {
                long deletedCount = response.getData().getDeleteCnt();
                logger.info("✓ 已删除文件的旧向量记录: {}, 删除记录数: {}", normalizedPath, deletedCount);
            }

        } catch (Exception e) {
            logger.warn("删除旧向量记录失败（可能是首次向量化入库）: {}", e.getMessage());
        }
    }

    /**
     * 构建元数据（包含文件信息）
     */
    private DocumentMetadata buildMetadata(String filePath, DocumentChunk chunk, int totalChunks) {
        DocumentMetadata metadata = new DocumentMetadata();
        
        // 标准化路径：使用统一的路径分隔符（正斜杠）用于存储，确保跨平台一致性
        Path path = Paths.get(filePath).normalize();
        String normalizedPath = path.toString().replace(File.separator, "/");
        
        // 文件信息
        Path fileName = path.getFileName();
        String fileNameStr = fileName != null ? fileName.toString() : "";
        String extension = "";
        int dotIndex = fileNameStr.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileNameStr.substring(dotIndex);
        }
        
        metadata.setSource(normalizedPath);
        metadata.setExtension(extension);
        metadata.setFileName(fileNameStr);
        
        // 分片信息
        metadata.setChunkIndex(chunk.getChunkIndex());
        metadata.setTotalChunks(totalChunks);
        
        // 标题信息
        metadata.setTitle(chunk.getTitle());
        
        return metadata;
    }

    /**
     * 将文档分片的向量记录插入 Milvus。
     */
    private void insertToMilvus(String content, List<Float> vector,
                                DocumentMetadata metadata, int chunkIndex) throws Exception {
        try {
            // 确保 collection 已加载
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build()
            );

            if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
                throw new RuntimeException("加载 collection 失败: " + loadResponse.getMessage());
            }

            // 生成唯一 ID（使用 source + 分片序号）保证同一文件的同一分片具有稳定 ID，便于后续去重或替换
            String source = metadata.getSource();
            String id = UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes()).toString();

            // 构建字段数据
            List<InsertParam.Field> fields = new ArrayList<>();
            
            // ID 字段
            fields.add(new InsertParam.Field("id", Collections.singletonList(id)));
            
            // content 字段
            fields.add(new InsertParam.Field("content", Collections.singletonList(content)));
            
            // vector 字段
            fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));
            
            // metadata 字段（JSON 对象），这里使用 Gson 将 DocumentMetadata 序列化为 JsonObject
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
            fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

            // 构建插入参数
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            // 执行插入并检查返回状态
            R<MutationResult> insertResponse = milvusClient.insert(insertParam);

            if (insertResponse.getStatus() != 0) {
                throw new RuntimeException("插入文档分片向量记录失败: " + insertResponse.getMessage());
            }

            logger.debug("文档分片向量记录插入成功: id={}, source={}, chunk={}", id, source, chunkIndex);

        } catch (Exception e) {
            logger.error("插入文档分片向量记录到 Milvus 失败", e);
            throw e;
        }
    }

    /**
     * 向量化入库结果类型（IndexingResult）。
     */
}
