package org.example.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.example.config.MilvusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Milvus 客户端工厂
 * <p>
 * 【这是你要写的文件】本轮只做"连接"，集合的创建留到阶段4。
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
        return client;


    }
}