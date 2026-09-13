package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 连接配置
 *
 * 【这个文件已给你，不用写】重点理解 @ConfigurationProperties：
 * 它把 application.yml 里 milvus.* 下面的配置自动绑定到这里的字段上。
 * 对比一下：如果用 @Value("${milvus.host}")，就得在每个字段上写一遍，类型也没法校验。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String database;
    private Long timeout;
}