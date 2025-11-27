/**
 * @author qkcao
 * @date 2025/9/16 18:22
 */
package com.rental.guard.ai.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class OffsetLimit {

    public final int offset;

    public int limit;

    public static final int LIMIT_MAX = 50;

    public static final int LIMIT = 10;


    public static int pageSize(int offset, int limit) {
        if (offset < limit) {
            return 0;
        }
        return (offset / limit) + 1;
    }

    public int pageSize() {
        if (this.offset < this.limit) {
            return 0;
        }
        return (this.offset / this.limit) + 1;
    }

    public static OffsetLimit normalize(Integer offset, Integer limit) {
        return normalize(offset, limit, LIMIT_MAX);
    }

    public static OffsetLimit normalize(Integer offset, Integer limit, int maxLimit) {
        if (offset == null) {
            offset = 0;
        } else if (offset < 0) {
            offset = 0;
        }

        if (limit == null) {
            limit = LIMIT;
        } else if (limit < 0) {
            limit = LIMIT;
        } else if (limit > maxLimit) {
            limit = maxLimit;
        }

        return new OffsetLimit(offset, limit);
    }
}
