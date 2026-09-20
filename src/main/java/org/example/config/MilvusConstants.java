package org.example.config;

/**
 * Milvus 相关常量
 */
public final class MilvusConstants {

    /** 集合名 */
    public static final String MILVUS_COLLECTION_NAME = "biz";

    /**
     * 向量维度 —— 必须和 embedding 模型返回的维度一致！
     * text-embedding-v4 默认返回 1024 维
     */
    public static final int VECTOR_DIM = 1024;

    /** id 字段最大长度（Milvus 的 VarChar 必须指定长度） */
    public static final int ID_MAX_LENGTH = 256;

    /** content 字段最大长度 */
    public static final int CONTENT_MAX_LENGTH = 8192;

    private MilvusConstants() {
    }
}