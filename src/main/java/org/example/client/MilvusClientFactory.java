package org.example.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import org.example.config.MilvusConstants;
import org.example.config.MilvusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Milvus 客户端工厂
 * <p>
 * 思考题：为什么不直接用 new MilvusServiceClient(...) 而要做成工厂 + Bean？
 * 提示：这是"连接型资源"，一个进程应该只维护一份连接。
 */
@Component
public class MilvusClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(MilvusClientFactory.class);

    @Autowired
    private MilvusProperties milvusProperties;

    /**
     * 创建并连接 Milvus
     */
    public MilvusServiceClient createClient() {
        logger.info("正在连接 Milvus: {}:{}", milvusProperties.getHost(), milvusProperties.getPort());

        // 1: 用 ConnectParam.newBuilder() 组装连接参数
        //         需要三个：withHost(host)、withPort(port)、withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
        //         注意 timeout 字段是 Long，注意单位是毫秒
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS)//这是连接超时时间，单位毫秒

                ;

        //
        // 2: 如果 username 不为 null 且不为空，再追加 .withAuthorization(username, password)
        //         提示：builder 是链式的，可以在 if 里对同一个 builder 变量继续 .withXxx()
        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        // 3: 返回 new MilvusServiceClient(builder.build())
        //         易错点：构造函数要的是 build() 之后的 ConnectParam，不是 Builder 本身
        MilvusServiceClient client = new MilvusServiceClient(builder.build());
        logger.info("Milvus 客户端创建成功");
        // 连接成功后，确保集合存在
        ensureCollection(client);
        return client;


    }
    /**
     * 集合不存在就创建（幂等：重复调用不会出问题）
     * 这是"应用自举"的思路 —— 不需要人工去 Milvus 里建表
     */
    private void ensureCollection(MilvusServiceClient client) {
        // 先问 Milvus：这个集合存在吗？
        R<Boolean> hasResponse = client.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .build());

        // R 是统一返回包装：status != 0 表示调用失败
        if (hasResponse.getStatus() != 0) {
            throw new RuntimeException("检查集合失败: " + hasResponse.getMessage());
        }

        // Boolean.TRUE.equals(...) 的写法比直接 getData() 更安全（避免 null 拆箱 NPE）
        if (Boolean.TRUE.equals(hasResponse.getData())) {
            logger.info("集合 '{}' 已存在", MilvusConstants.MILVUS_COLLECTION_NAME);
            return;                                         // 已存在，什么都不用做
        }

        logger.info("集合 '{}' 不存在，正在创建...", MilvusConstants.MILVUS_COLLECTION_NAME);
        createCollection(client);                           // 建集合（定义字段）
        createIndex(client);                                // 建向量索引
        logger.info("集合 '{}' 创建完成", MilvusConstants.MILVUS_COLLECTION_NAME);
    }
    /**
     * 创建集合：核心是定义 schema（有哪些字段、什么类型）
     */
    private void createCollection(MilvusServiceClient client) {
        // 字段1：id —— 字符串主键
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)              // VarChar = 变长字符串
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)// VarChar 必须指定最大长度
                .withPrimaryKey(true)                        // 标记为主键
                .build();

        // 字段2：vector —— 向量字段，维度必须和 embedding 模型一致
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)          // 浮点向量
                .withDimension(MilvusConstants.VECTOR_DIM)   // 1024 维
                .build();

        // 字段3：content —— 存原文，检索命中后要把它返回给大模型
        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();

        // 字段4：metadata —— JSON 类型，存文件名、分片序号、标题等"附加信息"
        // 用 JSON 而不是拆成多个字段，是因为将来可能加新元数据，改 schema 成本高
        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        // 组装 schema
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)               // 禁止写入未定义的字段（更严格，及早暴露错误）
                .addFieldType(idField)                       // 逐个加入字段
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();

        // 真正创建集合
        R<RpcStatus> response = client.createCollection(
                CreateCollectionParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .withDescription("Business knowledge collection")
                        .withSchema(schema)
                        .withShardsNum(2)                    // 分片数：小数据量 2 足够
                        .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("创建集合失败: " + response.getMessage());
        }
    }
    /**
     * 为 vector 字段创建索引
     * 没有索引的话，Milvus 只能暴力扫描（慢），或者直接不给检索
     */
    private void createIndex(MilvusServiceClient client) {
        R<RpcStatus> response = client.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                        .withFieldName("vector")
                        /**
                         * 索引类型 IVF_FLAT：
                         *   IVF = 倒排文件索引，先把向量聚类成 nlist 个簇，检索时只扫最近的几个簇
                         *   FLAT = 簇内精确计算，不做量化压缩
                         * 特点：召回率高、内存占用大。适合中小规模数据集（百万级以下）
                         * 对比：HNSW 更快更准但内存更贵；IVF_SQ8 省内存但会损失精度
                         */
                        .withIndexType(IndexType.IVF_FLAT)
                        /**
                         * 度量方式 L2（欧氏距离）：
                         *   ⚠️ 注意是"距离"，不是"相似度" —— 数值越小表示越相似！
                         *   这会影响后面检索结果的排序理解
                         */
                        .withMetricType(MetricType.L2)
                        .withExtraParam("{\"nlist\":128}")   // 聚成 128 个簇
                        .withSyncMode(Boolean.FALSE)         // 异步建索引，不阻塞启动
                        .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("创建索引失败: " + response.getMessage());
        }
    }



}