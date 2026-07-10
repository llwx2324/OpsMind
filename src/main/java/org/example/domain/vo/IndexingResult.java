package org.example.domain.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 索引结果汇总对象。
 *
 * <p>用于返回目录索引或批量索引的执行情况，包含成功/失败状态、文件统计、耗时以及失败文件明细。</p>
 */
@Getter
public class IndexingResult {

    /**
     * 索引是否成功。
     */
    @Setter
    private boolean success;

    /**
     * 被索引的目录路径。
     */
    @Setter
    private String directoryPath;

    /**
     * 扫描到的文件总数。
     */
    @Setter
    private int totalFiles;

    /**
     * 成功索引的文件数量。
     */
    private int successCount;

    /**
     * 索引失败的文件数量。
     */
    private int failCount;

    /**
     * 索引开始时间。
     */
    @Setter
    private LocalDateTime startTime;

    /**
     * 索引结束时间。
     */
    @Setter
    private LocalDateTime endTime;

    /**
     * 索引过程中的错误信息。
     */
    @Setter
    private String errorMessage;

    /**
     * 失败文件与错误原因映射。
     */
    private Map<String, String> failedFiles = new HashMap<>();

    /**
     * 成功数自增。
     */
    public void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * 失败数自增。
     */
    public void incrementFailCount() {
        this.failCount++;
    }

    /**
     * 计算索引耗时。
     *
     * @return 耗时毫秒数；若时间未完整设置则返回 0
     */
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    /**
     * 记录失败文件及其错误原因。
     *
     * @param filePath 文件路径
     * @param error 错误信息
     */
    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }
}
