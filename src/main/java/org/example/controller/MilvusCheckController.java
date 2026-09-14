package org.example.controller;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Milvus 健康检查接口
 *
 * 【这是你要写的文件】
 * 验收标准：浏览器访问 http://localhost:9900/milvus/health 返回 {"message":"ok","collections":[]}
 */
@RestController
@RequestMapping("/milvus")
public class MilvusCheckController {

    @Autowired
    private MilvusServiceClient milvusClient;

    /**
     * 健康检查：能列出集合，就说明
     *   ① 应用连上了 Milvus  ② Milvus 自己活着
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> simpleHealth() {
        Map<String, Object> result = new HashMap<>();

        //  1: 调用 milvusClient.showCollections(...) 列出所有集合
        //         参数用 ShowCollectionsParam.newBuilder().build()
        //         返回值类型是 R<ShowCollectionsResponse>，R 是 Milvus SDK 的统一返回包装
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder().build());

            //
            //  2: 判断 response.getStatus() == 0 表示成功，此时：
            //           result.put("message", "ok");
            //           result.put("collections", response.getData().getCollectionNamesList());
            //           return ResponseEntity.ok(result);
            if (response.getStatus() == 0) {
                result.put("message", "ok");
                result.put("collections", response.getData().getCollectionNamesList());
                return ResponseEntity.ok(result);
                //  3: status 不为 0 说明 Milvus 返回了错误：
                //           把 response.getMessage() 放进 result，用 ResponseEntity.status(503).body(result) 返回
                //
            } else {
                result.put("message", response.getMessage());
                return ResponseEntity.status(503).body(result);
            }
            // ③ Milvus 进程挂了 / 网络不通时走这里
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(503).body(result);



            //  4: 整段代码用 try/catch(Exception e) 包住。
            //         Milvus 没启动时这里会抛异常（连不上），要把 e.getMessage() 放进 result 并返回 503。
            //         思考题：为什么健康检查接口不能把异常直接抛出去（让 Spring 返回 500）？
        }
    }

}