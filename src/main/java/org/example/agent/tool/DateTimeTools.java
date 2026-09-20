package org.example.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日期时间工具类,项目里最简单的工具，用于理解工具是怎么被暴露给agent的
 *
 */
@Component
public class DateTimeTools {
    @Tool(description = "获取当前日期时间")
    public String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
