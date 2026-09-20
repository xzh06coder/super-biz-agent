package org.example.service;

import com.google.gson.Gson;                                     // 用于把 Map 转 JSON
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;                            // 写操作（插入/删除）的返回体
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;           // 加载集合参数
import io.milvus.param.dml.DeleteParam;                          // 删除参数
import io.milvus.param.dml.InsertParam;                          // 插入参数
import org.example.config.MilvusConstants;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;                                             // 只用它的 separator 常量
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 向量索引服务
 * 职责：读文件 → 分片 → 向量化 → 写入 Milvus
 * 是本轮"入库链路"的总指挥
 */
@Service
public class VectorIndexService {

    private static final Logger logger = LoggerFactory.getLogger(VectorIndexService.class);

    @Autowired
    private MilvusServiceClient milvusClient;                    // 来自 MilvusConfig 的 Bean

    @Autowired
    private VectorEmbeddingService embeddingService;             // 文本 → 向量

    @Autowired
    private DocumentChunkService chunkService;                   // 文本 → 分片

    /**
     * 索引单个文件（对外唯一入口）
     *
     * 声明 throws Exception 而不是就地 catch：
     * 让调用方（IndexRunner）决定"这个文件失败了要不要继续处理下一个"
     */
    public void indexFile(String filePath) throws Exception {
        // Paths.get + normalize：把路径标准化（去掉 ./ 和多余的斜杠）
        Path path = Paths.get(filePath).normalize();

        // 不是普通文件（不存在 / 是目录）就直接拒绝
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        logger.info("开始索引文件: {}", path);

        // ===== 第 1 步：读文件 =====
        // Files.readString 默认按 UTF-8 读取（Java 11+ 的便捷方法）
        String content = Files.readString(path);
        logger.info("读取完成，内容长度: {} 字符", content.length());

        // ===== 第 2 步：先删旧数据 =====
        // 目的：同一个文件重复索引时不产生重复分片（幂等）
        deleteBySource(path);

        // ===== 第 3 步：分片 =====
        List<DocumentChunk> chunks = chunkService.chunkDocument(content, path.toString());
        if (chunks.isEmpty()) {
            logger.warn("没有分片，跳过: {}", path);
            return;
        }

        // ===== 第 4 步：批量向量化 =====
        // 先把所有分片的正文抽成一个列表
        List<String> texts = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }
        // 一次请求把所有分片都向量化（不是循环调单条）
        List<List<Float>> vectors = embeddingService.generateEmbeddings(texts);

        // ===== 第 5 步：逐条写入 Milvus =====
        String source = normalize(path);                         // 统一的路径表示，用于删除和元数据
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);                 // 第 i 个分片
            Map<String, Object> metadata = buildMetadata(path, chunk, chunks.size());
            insert(source, chunk, vectors.get(i), metadata);     // 分片 + 它的向量 + 元数据
        }

        logger.info("文件索引完成: {}，共 {} 个分片", path, chunks.size());
    }

    /**
     * 删除某个文件之前索引的数据
     * 实现方式：按 metadata 里的 _source 字段（文件路径）匹配删除
     */
    private void deleteBySource(Path path) {
        try {
            String source = normalize(path);

            // 构造 Milvus 的删除表达式，语法类似 SQL 的 where
            // metadata["_source"] == "aiops-docs/cpu_high_usage.md"
            String expr = String.format("metadata[\"_source\"] == \"%s\"", source);

            // ⚠️ 删除操作要求集合已加载到内存（Milvus 的机制：数据在磁盘，查询/删除要先 load）
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build());

            R<MutationResult> response = milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build());

            if (response.getStatus() == 0) {
                // getDeleteCnt() 返回这次删掉了几条记录
                logger.info("已清理旧数据: {}，删除 {} 条", source, response.getData().getDeleteCnt());
            }
        } catch (Exception e) {
            // 首次索引时集合里没有这个文件的数据，删除失败是正常现象，所以只 warn 不抛
            logger.warn("清理旧数据失败（首次索引时正常）: {}", e.getMessage());
        }
    }

    /**
     * 插入一条向量
     */
    private void insert(String source, DocumentChunk chunk, List<Float> vector,
                        Map<String, Object> metadata) {
        /**
         * ★ 幂等设计的关键：用"路径 + 分片序号"生成确定性 UUID
         *   nameUUIDFromBytes 保证：同样的输入永远得到同样的 UUID
         *   所以同一个文件重复索引，每条记录的 id 都不变
         * 对比：UUID.randomUUID() 每次都不一样，重复索引会产生一堆重复数据
         */
        String id = UUID.nameUUIDFromBytes(
                (source + "_" + chunk.getChunkIndex()).getBytes()).toString();

        // Milvus 插入数据的方式：按"字段"组织，每个字段给一个值的列表
        List<InsertParam.Field> fields = new ArrayList<>();
        // Collections.singletonList(x) 生成只含一个元素、不可变的列表
        fields.add(new InsertParam.Field("id", Collections.singletonList(id)));
        fields.add(new InsertParam.Field("content", Collections.singletonList(chunk.getContent())));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

        // metadata 是 JSON 字段，需要 gson 把 Map 转成 JsonObject
        JsonObject metadataJson = new Gson().toJsonTree(metadata).getAsJsonObject();
        fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

        R<MutationResult> response = milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFields(fields)
                .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("插入失败: " + response.getMessage());
        }
    }

    /**
     * 构建元数据（存进 Milvus 的 JSON 字段）
     * 这些信息的用途：
     *   _source     → 删除旧数据时用来匹配
     *   _file_name  → 检索命中后告诉用户"来自哪个文件"
     *   chunkIndex  → 判断分片顺序
     *   title       → 检索命中后告诉用户"来自哪一节"
     */
    private Map<String, Object> buildMetadata(Path path, DocumentChunk chunk, int totalChunks) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("_source", normalize(path));
        metadata.put("_file_name", path.getFileName().toString());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("totalChunks", totalChunks);

        // 标题可能为 null（无标题的文档），判空后再放
        if (chunk.getTitle() != null && !chunk.getTitle().isEmpty()) {
            metadata.put("title", chunk.getTitle());
        }
        return metadata;
    }

    /**
     * 统一路径分隔符
     * ⚠️ Windows 上是反斜杠 \，而反斜杠在 Milvus 表达式里是转义字符，
     *    不转换的话删除表达式会解析失败
     */
    private String normalize(Path path) {
        return path.toString().replace(File.separator, "/");
    }
}