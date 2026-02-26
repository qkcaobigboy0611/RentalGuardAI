/**
 * @author qkcao
 * @date 2026/2/26 17:03
 */
package com.rental.guard.ai.domain.service.Scenario;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {

    // 1. 租金提取：匹配 "租金5000"、"月租 4800"、"5500/月"、"价格为6000.5元"
    private static final Pattern RENT_PATTERN = Pattern.compile("(?:租金|月租|价格|金额)[:：\\s]{0,3}(\\d+(?:\\.\\d+)?)");

    // 2. 押金倍数提取：匹配 "押一"、"押2"、"押金为三个月"、"两个月租金作为押金"
    private static final Pattern DEPOSIT_PATTERN = Pattern.compile("(?:押|押金为|个月租金)[:：\\s]{0,3}([一二三123])");

    // 3. 百分比提取：匹配 "10%"、"上涨 8.5%"、"百分之五"
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern CHINESE_PERCENT_PATTERN = Pattern.compile("百分之([一二三四五六七八九十\\d]+)");

    /**
     * 提取租金数值
     */
    public static double extractRent(String text) {
        if (text == null) return 0.0;
        Matcher m = RENT_PATTERN.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0.0;
    }

    /**
     * 提取押金比例（几个月租金）
     */
    public static double extractDepositRatio(String text) {
        if (text == null) return 0.0;
        Matcher m = DEPOSIT_PATTERN.matcher(text);
        if (m.find()) {
            String val = m.group(1);
            return chineseToNumber(val);
        }
        return 0.0;
    }

    /**
     * 提取百分比涨幅 (返回小数，如 0.05)
     */
    public static double extractRate(String text) {
        if (text == null) return 0.0;

        // 尝试匹配数字型百分比: 10%
        Matcher m1 = PERCENT_PATTERN.matcher(text);
        if (m1.find()) {
            return Double.parseDouble(m1.group(1)) / 100.0;
        }

        // 尝试匹配中文型百分比: 百分之五
        Matcher m2 = CHINESE_PERCENT_PATTERN.matcher(text);
        if (m2.find()) {
            return chineseToNumber(m2.group(1)) / 100.0;
        }

        return 0.0;
    }

    /**
     * 辅助：简单中文数字转 double
     */
    private static double chineseToNumber(String ch) {
        switch (ch) {
            case "一": case "1": return 1.0;
            case "二": case "2": return 2.0;
            case "三": case "3": return 3.0;
            case "四": case "4": return 4.0;
            case "五": case "5": return 5.0;
            case "六": case "6": return 6.0;
            case "七": case "7": return 7.0;
            case "八": case "8": return 8.0;
            case "九": case "9": return 9.0;
            case "十": case "10": return 10.0;
            default: return 0.0;
        }
    }
}
