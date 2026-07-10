package org.example.controller;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;
import org.example.domain.vo.MilvusHealthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Milvus 健康检查控制器。
 *
 * <p>提供一个轻量级健康检查接口，用于验证 Milvus 服务是否可用以及当前可访问的 collection 列表。
 * 该接口主要服务于运维检查、联调验证和前端状态展示。</p>
 */
@RestController
@RequestMapping("/milvus")
public class MilvusCheckController {

    @Autowired
    private MilvusServiceClient milvusClient;

    /**
     * 简单健康检查接口。
     *
     * <p>调用 Milvus 的 showCollections 接口确认客户端连接与服务状态；成功时返回 collection 列表，
     * 失败时返回 503 并附带错误信息。</p>
     *
     * @return MilvusHealthResponse 包含健康状态、collection 列表或错误信息
     */
    @GetMapping("/health")
    public ResponseEntity<MilvusHealthResponse> simpleHealth() {
        MilvusHealthResponse result = new MilvusHealthResponse();

        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(
                    ShowCollectionsParam.newBuilder().build()
            );

            if (response.getStatus() == 0) {
                result.setMessage("ok");
                result.setCollections(response.getData().getCollectionNamesList());
                return ResponseEntity.ok(result);
            }

            result.setMessage(response.getMessage());
            return ResponseEntity.status(503).body(result);
        } catch (Exception e) {
            result.setError(e.getMessage());
            return ResponseEntity.status(503).body(result);
        }
    }
}
