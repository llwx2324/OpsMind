package org.example.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.example.domain.vo.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量搜索服务。
 *
 * <p>负责将用户查询向量化后调用 Milvus 搜索并将结果映射为应用层的 SearchResult。
 * 包含搜索参数配置（距离度量、nprobe 等）和结果解析逻辑，调用方无需关心 Milvus 的底层细节。</p>
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    /**
     * Milvus 客户端实例，负责与 Milvus Server 通信。
     */
    @Autowired
    private MilvusServiceClient milvusClient;

    /**
     * 嵌入向量服务，用于将查询文本转换为向量表示。
     */
    @Autowired
    private VectorEmbeddingService embeddingService;

    /**
     * 搜索与查询最相似的文档。
     *
     * <p>步骤：
     * <ol>
     *   <li>将查询文本生成向量</li>
     *   <li>构建 Milvus SearchParam，指定集合、向量字段、metric、nprobe 等</li>
     *   <li>解析 Milvus 返回的 SearchResults 并封装为 SearchResult 列表</li>
     * </ol>
     * </p>
     *
     * @param query 查询文本（非空）
     * @param topK  返回最相似的 K 个结果
     * @return 按相似度排序的 SearchResult 列表；发生错误时抛出运行时异常
     */
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("开始搜索相似文档, 查询: {}, topK: {}", query, topK);

            // 1. 将查询文本向量化
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            ensureCollectionLoaded();
            logger.debug("查询向量生成成功, 维度: {}", queryVector.size());

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME) // 从 biz 集合中搜索
                    .withVectorFieldName("vector") // 指定集合中的存储向量的字段名
                    .withVectors(Collections.singletonList(queryVector)) // 指定查询向量列表
                    .withTopK(topK) // 指定返回的最相似向量数量
                    .withMetricType(io.milvus.param.MetricType.L2) // 使用 L2 距离（欧氏距离）度量
                    .withOutFields(List.of("id", "content", "metadata")) // 指定返回的字段，包括 id、content 和 metadata
                    // Milvus 特有的性能优化参数。nprobe 值为 10 表示在搜索时探测 10 个分区单元，这是精确度和性能之间的平衡——值越大精确度越高但搜索速度越慢。
                    .withParams("{\"nprobe\":10}")
                    .build();

            // 3. 执行搜索。R 是 Milvus 客户端的响应封装类，包含状态码、消息和数据
            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败: " + searchResponse.getMessage());
            }

            // 4. 解析搜索结果并映射到应用层对象
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            // 注意：SearchResultsWrapper 提供按行访问的方法，需要依据所在行索引提取字段数据。
            // 此处按第 0 行（单向量查询）遍历记录并进行类型转换，若字段类型或命名发生变更可能抛出 ClassCastException。
            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                // 解析 metadata（可能为 Map 或其他结构），使用 toString 保证不会抛出空指针
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            logger.info("搜索完成, 找到 {} 个相似文档", results.size());
            return results;

        } catch (Exception e) {
            logger.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }

    private void ensureCollectionLoaded() {
        R<RpcStatus> loadResponse = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .build()
        );

        if (loadResponse.getStatus() != 0 && loadResponse.getStatus() != 65535) {
            throw new RuntimeException("Load collection failed: " + loadResponse.getMessage());
        }
    }

}
