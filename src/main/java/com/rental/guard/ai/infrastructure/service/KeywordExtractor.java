/**
 * @author qkcao
 * @date 2025/12/31 16:20
 */
package com.rental.guard.ai.infrastructure.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KeywordExtractor {

    // 欺诈相关关键词库
    private static final Set<String> FRAUD_KEYWORDS = new HashSet<>(Arrays.asList(
            "微信", "QQ", "转账", "投资", "理财", "赚钱", "保证金",
            "押金", "定金", "汇款", "付款", "支付宝", "银行卡",
            "长租", "老板", "公司", "办公", "合同", "签约",
            "见面", "看房", "照片", "视频", "信任", "诚意",
            "优惠", "折扣", "急租", "尽快", "马上", "立即"
    ));

    // 平台相关词汇
    private static final Set<String> PLATFORM_KEYWORDS = new HashSet<>(Arrays.asList(
            "微信", "QQ", "钉钉", "百度网盘", "telegram", "whatsapp",
            "skype", "line", "kakao", "wechat"
    ));

    // 停止词
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "这", "那", "什么", "怎么", "吗", "吧", "啊", "呢", "哦", "嗯", "哈", "哼", "唉"
    ));

    // 中文分词模式
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4e00-\u9fa5]+");

    /**
     * 提取关键词
     */
    public List<String> extractKeywords(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }

        try {
            // 1. 提取中文词汇
            List<String> words = extractChineseWords(text);

            // 2. 过滤停止词
            words = words.stream()
                    .filter(word -> !STOP_WORDS.contains(word))
                    .filter(word -> word.length() >= 2) // 至少两个字
                    .collect(Collectors.toList());

            // 3. 优先选择欺诈相关关键词
            List<String> fraudKeywords = words.stream()
                    .filter(FRAUD_KEYWORDS::contains)
                    .distinct()
                    .collect(Collectors.toList());

            // 4. 如果没有欺诈关键词，使用高频词
            if (fraudKeywords.isEmpty() && !words.isEmpty()) {
                fraudKeywords = selectTopFrequentWords(words, 5);
            }

            log.debug("关键词提取结果 - 文本长度: {}, 关键词: {}", text.length(), fraudKeywords);
            return fraudKeywords;

        } catch (Exception e) {
            log.error("关键词提取失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 提取平台关键词
     */
    public List<String> extractPlatformKeywords(String text) {
        List<String> allKeywords = extractKeywords(text);
        return allKeywords.stream()
                .filter(PLATFORM_KEYWORDS::contains)
                .collect(Collectors.toList());
    }

    /**
     * 提取金钱相关关键词
     */
    public List<String> extractMoneyKeywords(String text) {
        Set<String> moneyKeywords = new HashSet<>(Arrays.asList(
                "钱", "元", "块", "转账", "汇款", "付款", "定金", "押金", "保证金",
                "租金", "费用", "价格", "价", "款", "金额", "收费", "付费"
        ));

        List<String> allKeywords = extractKeywords(text);
        return allKeywords.stream()
                .filter(moneyKeywords::contains)
                .collect(Collectors.toList());
    }

    /**
     * 提取中文词汇
     */
    private List<String> extractChineseWords(String text) {
        List<String> words = new ArrayList<>();

        // 简单的中文分词：按标点分割，然后提取连续的中文字符
        String[] sentences = text.split("[，。！？；,.;!?\\s]+");

        for (String sentence : sentences) {
            StringBuilder wordBuilder = new StringBuilder();

            for (char c : sentence.toCharArray()) {
                if (isChineseCharacter(c)) {
                    wordBuilder.append(c);
                } else if (wordBuilder.length() > 0) {
                    words.add(wordBuilder.toString());
                    wordBuilder.setLength(0);
                }
            }

            if (wordBuilder.length() > 0) {
                words.add(wordBuilder.toString());
            }
        }

        return words;
    }

    /**
     * 选择高频词
     */
    private List<String> selectTopFrequentWords(List<String> words, int topN) {
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (String word : words) {
            frequencyMap.put(word, frequencyMap.getOrDefault(word, 0) + 1);
        }

        return frequencyMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 判断是否为中文字符
     */
    private boolean isChineseCharacter(char c) {
        return CHINESE_PATTERN.matcher(String.valueOf(c)).matches();
    }
}
