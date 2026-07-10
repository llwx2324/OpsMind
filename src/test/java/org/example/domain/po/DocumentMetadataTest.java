package org.example.domain.po;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMetadataTest {

    @Test
    void serializesMilvusFieldNames() {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setSource("uploads/cpu_high_usage.md");
        metadata.setExtension(".md");
        metadata.setFileName("cpu_high_usage.md");
        metadata.setChunkIndex(2);
        metadata.setTotalChunks(5);
        metadata.setTitle("CPU 告警");

        String json = new Gson().toJson(metadata);

        assertThat(json)
                .contains("\"_source\":\"uploads/cpu_high_usage.md\"")
                .contains("\"_extension\":\".md\"")
                .contains("\"_file_name\":\"cpu_high_usage.md\"")
                .contains("\"chunkIndex\":2")
                .contains("\"totalChunks\":5")
                .contains("\"title\":\"CPU 告警\"");
    }
}
