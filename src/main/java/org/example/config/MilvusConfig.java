package org.example.config;

import io.milvus.client.MilvusServiceClient;
import org.example.client.MilvusClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Milvus 配置类
 *
 * 【这是你要写的文件】
 * 这个类的唯一职责：把 MilvusServiceClient 注册成 Spring Bean，
 * 这样后面的 Service 层只要 @Autowired MilvusServiceClient 就能拿到同一个客户端。
 */
@Configuration
public class MilvusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 方法返回值会被 Spring 放进容器，方法名就是 Bean 的名字
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        // 1: 调用 milvusClientFactory.createClient() 创建客户端，
        //         把结果同时赋值给上面那个 milvusClient 字段（为什么？看 3）
        logger.info("开始初始化milvus客户端");
        milvusClient = milvusClientFactory.createClient();
        // 2: 打一行日志，然后 return 这个客户端
        logger.info("milvus客户端初始化成功");
        return milvusClient;
    }

    /**
     * 应用关闭时的钩子
     * 3: milvusClient 不为 null 时调用 close() 并打日志。
     *         思考题：如果不 close 会怎样？为什么要专门把客户端存到字段里？
     */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            logger.info("开始关闭milvus客户端连接");
            milvusClient.close();
            logger.info("milvus客户端关闭成功");
        }
    }
}