package org.example.dto;

import lombok.Data;

/**
 * 文档分片
 */
@Data
public class DocumentChunk {

    /** 分片内容 */
    private String content;

    /** 分片序号，从 0 开始 */
    private int chunkIndex;

    /** 所属章节标题（Markdown 的 ## 标题），用于给分片补充上下文 */
    private String title;

    /** 在原文中的起始位置 */
    private int startIndex;

    /** 在原文中的结束位置 */
    private int endIndex;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int chunkIndex, String title, int startIndex, int endIndex) {
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{index=" + chunkIndex
                + ", title='" + title + "'"
                + ", contentLength=" + (content == null ? 0 : content.length()) + "}";
    }
}