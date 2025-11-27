/**
 * @author qkcao
 * @date 2025/9/17 10:05
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class IpLocation {
    private String ip;
    private String country;
    private String province;
    private String city;

    public static IpLocation Unknown = IpLocation.builder().country("未知").province("未知").build();
}
