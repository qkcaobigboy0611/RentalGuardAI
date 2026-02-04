/**
 * @author qkcao
 * @date 2026/1/27 10:29
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.util.List;

@Data
public class FraudPattern {
    private String name;
    private List<String> indicators;

    public FraudPattern(String name, List<String> indicators) {
        this.name = name;
        this.indicators = indicators;
    }
}
