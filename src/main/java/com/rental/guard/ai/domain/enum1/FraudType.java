/**
 * @author qkcao
 * @date 2026/1/28 14:46
 */
package com.rental.guard.ai.domain.enum1;

public enum FraudType {
    // 房源相关欺诈
    FAKE_LISTING,           // 虚假房源
    PRICE_SCAM,             // 价格欺诈
    PROPERTY_MISREPRESENTATION, // 房屋信息虚假描述
    DOUBLE_LISTING,         // 一房多租

    // 房东/中介欺诈
    FAKE_LANDLORD,          // 假房东
    UNAUTHORIZED_AGENT,     // 无资质中介
    DEPOSIT_SCAM,           // 押金诈骗

    // 交易过程欺诈
    PHISHING,               // 钓鱼链接
    WIRE_FRAUD,             // 电汇诈骗
    CONTRACT_FRAUD,         // 合同欺诈

    // 身份欺诈
    IDENTITY_THEFT,         // 身份盗用
    FORGED_DOCUMENTS        // 伪造证件
}
