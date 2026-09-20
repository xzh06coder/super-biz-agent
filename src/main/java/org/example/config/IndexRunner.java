package org.example.config;

import org.example.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;          // 启动参数
import org.springframework.boot.ApplicationRunner;           // ★ 启动后钩子的接口
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;

/**
 * 启动后自动索引文档
 *
 * ApplicationRunner 是什么？
 *   实现这个接口的 Bean，会在 Spring 容器完全启动之后被调用一次 run() 方法
 *   和它类似的还有 CommandLineRunner，区别：
 *     ApplicationRunner 的参数是结构化的 ApplicationArguments
 *     CommandLineRunner 的参数是原始的 String[]
 */
@Component
public class IndexRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(IndexRunner.class);

    @Autowired
    private VectorIndexService vectorIndexService;

    /**
     * @Value 里的冒号后面是"默认值"
     * 写法 "${index.on-startup.dir:aiops-docs}" 意思是：
     *   yml 里配了 index.on-startup.dir 就用配的，没配就用 aiops-docs
     * 好处：不用为了一个参数去改 application.yml
     */
    @Value("${index.on-startup.dir:aiops-docs}")
    private String docsDir;

    /** 开关：想跳过自动索引时（比如只想调别的接口），把它设成 false */
    @Value("${index.on-startup.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        // 开关关掉就直接返回
        if (!enabled) {
            return;
        }

        // ⚠️ 默认是相对路径，所以必须在"项目根目录"启动应用，否则找不到目录
        File dir = Paths.get(docsDir).toFile();
        if (!dir.isDirectory()) {
            logger.warn("文档目录不存在，跳过自动索引: {}", dir.getAbsolutePath());
            return;
        }

        /**
         * listFiles(过滤器) 只挑出 .md 和 .txt
         * 这是个函数式接口（FilenameFilter），(d, name) 分别是目录和文件名
         */
        File[] files = dir.listFiles((d, name) -> name.endsWith(".md") || name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            logger.warn("目录里没有可索引的文档: {}", dir.getAbsolutePath());
            return;
        }

        logger.info("开始自动索引 {} 个文档...", files.length);
        long start = System.currentTimeMillis();             // 记开始时间，最后算总耗时

        int success = 0;
        int failed = 0;

        for (File file : files) {
            try {
                vectorIndexService.indexFile(file.getAbsolutePath());
                success++;
            } catch (Exception e) {
                /**
                 * ★ 关键设计：单个文件失败不能中断整批
                 *    只记数 + 打日志，循环继续
                 *    这就是"容错"—— 一个坏文件不该让整个系统不可用
                 */
                failed++;
                logger.error("索引失败: {}", file.getName(), e);
            }
        }

        logger.info("自动索引完成: 成功 {} 个，失败 {} 个，耗时 {} ms",
                success, failed, System.currentTimeMillis() - start);
    }
}