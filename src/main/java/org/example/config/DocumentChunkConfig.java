package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 分片配置，对应 application.yml 里的 document.chunk.*
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {

    /** 每个分片最大字符数 */
    private int maxSize = 800;

    /** 相邻分片的重叠字符数 */
    private int overlap = 100;
}