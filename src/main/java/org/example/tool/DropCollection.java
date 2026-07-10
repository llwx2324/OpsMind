package org.example.tool;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;

/**
 * 删除 Milvus Collection 的工具类。
 *
 * <p>这是一个运维/开发辅助入口，用于删除旧 collection，使应用下次启动时重新创建 collection 及其 vector 字段索引结构。
 * 由于会直接删除 Milvus 中的 collection 及其中所有向量记录，请仅在确认环境和数据可丢弃时使用。</p>
 */
public class DropCollection {
    
    /**
     * 命令行入口：连接 Milvus、检查 collection 是否存在并按需删除。
     *
     * @param args 命令行参数（当前未使用）
     */
    public static void main(String[] args) {
        MilvusServiceClient client = null;
        
        try {
            // 连接到 Milvus
            System.out.println("正在连接到 Milvus localhost:19530...");
            client = new MilvusServiceClient(
                ConnectParam.newBuilder()
                    .withHost("localhost")
                    .withPort(19530)
                    .build()
            );
            System.out.println("✓ 连接成功");
            
            String collectionName = "biz";
            
            // 检查 Collection 是否存在
            R<Boolean> hasResponse = client.hasCollection(
                HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build()
            );
            
            if (hasResponse.getData()) {
                System.out.println("发现 Collection: " + collectionName);
                System.out.println("正在删除...");
                
                // 删除 Collection
                R<RpcStatus> dropResponse = client.dropCollection(
                    DropCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
                );
                
                if (dropResponse.getStatus() == 0) {
                    System.out.println("✓ Collection 已成功删除");
                    System.out.println("\n请重启 Spring Boot 应用，它会自动创建新的 FloatVector Collection");
                } else {
                    System.err.println("✗ 删除失败: " + dropResponse.getMessage());
                }
            } else {
                System.out.println("Collection '" + collectionName + "' 不存在");
            }
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            // 保留简单错误输出，避免在命令行工具中引入额外日志依赖。
            for (StackTraceElement element : e.getStackTrace()) {
                System.err.println("    at " + element);
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
