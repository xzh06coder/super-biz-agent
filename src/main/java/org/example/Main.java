package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类
 *
 * 【这是你要写的文件】填掉下面两个 TODO，一共两行。
 */
// TODO 1: 在 public class 上面加一个注解。
//         它的作用 = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan 三个注解合体。
//         提示：名字就叫"Spring Boot 应用"，导入 org.springframework.boot.autoconfigure 包下的那个。
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        // TODO 2: 启动 Spring Boot 应用。
        //         提示：静态方法 SpringApplication.run(...)，第一个参数是"启动类.class"，第二个是 args。
        SpringApplication.run(Main.class, args);
    }
}