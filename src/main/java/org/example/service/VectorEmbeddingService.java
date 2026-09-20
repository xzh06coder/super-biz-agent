package org.example.service;

// 以下都是阿里云 DashScope 原生 SDK 的类（不是 Spring AI 的）
import com.alibaba.dashscope.embeddings.TextEmbedding;          // 向量化客户端
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;     // 请求参数
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;    // 响应结果
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;// 单条结果
import com.alibaba.dashscope.utils.Constants;                   // SDK 的全局配置类
import jakarta.annotation.PostConstruct;                        // Bean 初始化后回调
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文本向量化服务
 * 职责：把一段文字变成 1024 个浮点数组成的向量
 */
@Service
public class VectorEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    /** 从 application.yml 的 dashscope.api.key 取值（值是 ${DASHSCOPE_API_KEY} 环境变量） */
    @Value("${dashscope.api.key}")
    private String apiKey;

    /** 模型名，yml 里配的是 text-embedding-v4 */
    @Value("${dashscope.embedding.model}")
    private String model;

    /** SDK 的客户端对象，在 init() 里创建 */
    private TextEmbedding textEmbedding;

    /**
     * @PostConstruct 表示：Spring 创建完这个 Bean、注入完所有字段之后，立刻调用本方法
     * 属于"Bean 生命周期"里的初始化钩子（对比 @PreDestroy 是销毁钩子）
     */
    @PostConstruct
    public void init() {
        // 提前校验配置，让错误在启动时就暴露，而不是等用户提问时才报错（fail-fast）
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置 DashScope API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        /**
         * ⚠️ 关键坑：DashScope 原生 SDK 不是"把 key 传给构造函数"，而是写在一个全局静态变量上
         * 也就是说整个 JVM 只有一份 —— 如果同时连多个账号，后设置的会覆盖前面的
         */
        Constants.apiKey = apiKey;

        // 创建客户端（此对象可复用，不用每次 new）
        this.textEmbedding = new TextEmbedding();

        // 打日志时脱敏：只显示前 8 位和后 4 位，避免 API Key 完整写进日志
        String masked = apiKey.length() > 8
                ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
                : "***";
        logger.info("向量化服务初始化完成，模型: {}，API Key: {}", model, masked);
    }

    /**
     * 单条文本向量化：内部委托给批量方法
     * 为什么要绕一层？—— 保证"单条"和"批量"走的是同一套错误处理逻辑，
     * 将来改错误处理只用改一个地方
     */
    public List<Float> generateEmbedding(String content) {
        List<List<Float>> result = generateEmbeddings(Collections.singletonList(content));
        if (result.isEmpty()) {
            throw new RuntimeException("向量化返回空结果");
        }
        // get(0) 取出第一条的结果
        return result.get(0);
    }

    /**
     * 批量向量化
     * ★ 性能关键：一次 HTTP 请求处理 N 条文本
     *   如果循环调用单条接口，7 个分片就是 7 次网络往返（每次几十~几百毫秒）
     */
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        // 空输入直接返回空列表，不发请求
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 构建请求参数：指定模型 + 要向量化的文本列表
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(model)
                    .texts(contents)
                    .build();

            // 发起调用（同步阻塞，直到拿到全部结果）
            TextEmbeddingResult response = textEmbedding.call(param);

            // 逐层判空：SDK 返回的结构是 response -> output -> embeddings，任何一层都可能为 null
            if (response == null
                    || response.getOutput() == null
                    || response.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("DashScope 返回空结果");
            }

            // SDK 返回的是 List<Double>，我们要的是 List<Float>
            // 原因：Milvus 的 FloatVector 字段要求的元素类型是 float
            List<List<Float>> vectors = new ArrayList<>();
            for (TextEmbeddingResultItem item : response.getOutput().getEmbeddings()) {
                List<Double> doubles = item.getEmbedding();
                // 提前指定容量，避免 ArrayList 反复扩容
                List<Float> floats = new ArrayList<>(doubles.size());
                for (Double d : doubles) {
                    // Double → Float 要显式转换，直接赋值编译不过
                    floats.add(d.floatValue());
                }
                vectors.add(floats);
            }

            logger.info("向量化完成，共 {} 条，维度 {}",
                    vectors.size(), vectors.isEmpty() ? 0 : vectors.get(0).size());
            return vectors;

        } catch (Exception e) {
            // 统一包一层异常，往上抛出带上下文的错误信息（原始异常放进 cause，不丢堆栈）
            logger.error("向量化失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
}