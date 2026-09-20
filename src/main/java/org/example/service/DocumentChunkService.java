package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片服务
 *
 * 分两阶段：
 *   阶段一：按 Markdown 标题（## xxx）切成"章节"—— 尊重文档原本的语义结构
 *   阶段二：对每个太长的章节，按段落累加切分，并给相邻分片加重叠
 *           （只有超长的章节才需要切，短章节直接作为一个分片）
 */
@Service
public class DocumentChunkService {

    // 日志对象，static final 是固定写法（每个类一个，不要每个实例一个）
    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);

    /**
     * 匹配 Markdown 标题的正则
     *   ^(#{1,6})    →  行首的 1~6 个 #（# 到 ######）
     *   \\s+         →  中间至少一个空格
     *   (.+)         →  标题文字（第 2 个捕获组）
     *   Pattern.MULTILINE → 让 ^ 能匹配"每一行的开头"，而不只是整个字符串的开头
     *                       没有这个标志，只能匹配文档第一行的标题
     */
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    // 从容器注入分片配置（maxSize / overlap）
    @Autowired
    private DocumentChunkConfig chunkConfig;

    /**
     * 分片入口
     *
     * @param content  文件的全部文本
     * @param filePath 文件路径，只用于打日志
     * @return 分片列表（按原文顺序）
     */
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        // 准备装结果的容器
        List<DocumentChunk> chunks = new ArrayList<>();

        // 防御：空文件直接返回空列表（注意用 isBlank 而非 isEmpty，
        // 因为"全是换行和空格"的文档也应该视为空）
        if (content == null || content.isBlank()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        // 第一步：按标题切成章节
        List<Section> sections = splitByHeading(content);

        // 第二步：逐章节细分；chunkIndex 是全局连续的编号，跨章节累加
        int chunkIndex = 0;
        for (Section section : sections) {
            // 处理这一个章节，起始编号是当前的 chunkIndex
            List<DocumentChunk> sectionChunks = chunkSection(section, chunkIndex);
            // 把这一章节产出的分片全部并入总结果
            chunks.addAll(sectionChunks);
            // 编号往后推：下一章节从"已产出的分片数"继续编号
            chunkIndex += sectionChunks.size();
        }

        logger.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    /**
     * 按 Markdown 标题把文档切成若干章节
     *
     * 算法：用正则从头扫，每遇到一个标题，就把"上一个标题到本标题之间"的文字算作一章
     */
    private List<Section> splitByHeading(String content) {
        List<Section> sections = new ArrayList<>();
        // matcher 是"扫描器"，find() 一次就前进到下一个匹配到的标题
        Matcher matcher = HEADING_PATTERN.matcher(content);

        // lastEnd：上一段文字结束的位置（下一段的开头）
        int lastEnd = 0;
        // currentTitle：当前正在处理的章节标题；文档开头（第一个标题之前）没有标题，所以是 null
        String currentTitle = null;

        // 循环：每次找到一个新的标题
        while (matcher.find()) {
            // 如果"上一个结束点"到"本标题开始处"之间还有文字，说明这是一章的内容
            if (lastEnd < matcher.start()) {
                // substring 取出这一段，trim 去掉首尾空白
                String text = content.substring(lastEnd, matcher.start()).trim();
                if (!text.isEmpty()) {
                    // 记录这一章：标题、正文、在原文中的起点
                    sections.add(new Section(currentTitle, text, lastEnd));
                }
            }
            // group(2) 是正则的第 2 个捕获组，也就是标题文字（不含 # 号）
            currentTitle = matcher.group(2).trim();
            // 下一段文字从这个标题的位置开始（标题本身会包含在下一段里）
            lastEnd = matcher.start();
        }

        // 循环结束：处理"最后一个标题之后"的剩余内容
        if (lastEnd < content.length()) {
            String text = content.substring(lastEnd).trim();
            if (!text.isEmpty()) {
                sections.add(new Section(currentTitle, text, lastEnd));
            }
        }

        // 极端情况：整个文档一个标题都没有 → 把全文当成唯一一章
        if (sections.isEmpty()) {
            sections.add(new Section(null, content, 0));
        }

        return sections;
    }

    /**
     * 对单个章节分片
     *
     * @param section    章节
     * @param startIndex 本章节分片的起始编号
     */
    private List<DocumentChunk> chunkSection(Section section, int startIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content;

        // 情况一：章节本身就够短 → 整章作为一个分片，不用切
        if (content.length() <= chunkConfig.getMaxSize()) {
            chunks.add(new DocumentChunk(
                    content,                                  // 内容
                    startIndex,                               // 编号
                    section.title,                            // 标题
                    section.startIndex,                       // 原文起点
                    section.startIndex + content.length()));  // 原文终点
            return chunks;
        }

        // 情况二：章节太长 → 按段落切
        // split("\n\n+") 表示"一个或多个连续换行"作为分隔符，这是 Markdown 段落的标准分界
        String[] paragraphs = content.split("\n\n+");

        // current：正在累积的这一块（StringBuilder 比字符串拼接高效，避免反复创建对象）
        StringBuilder current = new StringBuilder();
        // currentStart：当前这一块在原文中的起始位置
        int currentStart = section.startIndex;
        // index：当前要分配的分片编号
        int index = startIndex;

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;   // 空段落跳过
            }

            // 关键判断：如果"当前块 + 本段落"会超过上限，就先把当前块封存
            // current.length() > 0 是为了保证"当前块非空"，否则第一段就直接被切了
            if (current.length() > 0
                    && current.length() + p.length() > chunkConfig.getMaxSize()) {

                // 把当前累积的内容定稿
                String chunkContent = current.toString().trim();

                // 存成一个分片（注意 index++ 是先取值再自增）
                chunks.add(new DocumentChunk(chunkContent, index++, section.title,
                        currentStart, currentStart + chunkContent.length()));

                // ★ 重叠：取出刚定稿那块的"末尾 N 个字符"，作为新块的开头
                //   目的：如果一句话正好被切在边界上，靠这 N 个字把它"接上"
                String overlapText = tail(chunkContent, chunkConfig.getOverlap());

                // 用重叠文字开启新的一块
                current = new StringBuilder(overlapText);
                // 新块的起点 = 旧起点 + 旧长度 - 重叠长度
                currentStart = currentStart + chunkContent.length() - overlapText.length();
            }

            // 把这一段追加进当前块，并补两个换行（保持段落分隔）
            current.append(p).append("\n\n");
        }

        // 循环结束：把最后残留的一块也存成分片
        if (current.length() > 0) {
            String chunkContent = current.toString().trim();
            chunks.add(new DocumentChunk(chunkContent, index, section.title,
                    currentStart, currentStart + chunkContent.length()));
        }

        return chunks;
    }

    /**
     * 取字符串末尾的 n 个字符
     * 边界处理：n <= 0 返回空串；n 超过文本长度时最多取全文
     */
    private String tail(String text, int n) {
        if (n <= 0 || text.isEmpty()) {
            return "";
        }
        int size = Math.min(n, text.length());
        return text.substring(text.length() - size);
    }

    /**
     * 内部类：章节
     * 只在本类内部使用，所以 private static（static 表示不依赖外部类实例）
     * 字段用 final：创建后不再修改，更安全
     */
    private static class Section {
        final String title;
        final String content;
        final int startIndex;

        Section(String title, String content, int startIndex) {
            this.title = title;
            this.content = content;
            this.startIndex = startIndex;
        }
    }
}