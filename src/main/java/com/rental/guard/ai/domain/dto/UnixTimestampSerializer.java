/**
 * @author qkcao
 * @date 2025/9/17 10:06
 */
package com.rental.guard.ai.domain.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;

public class UnixTimestampSerializer extends JsonSerializer<Date> {
    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeNumber(value.getTime() / 1000);
    }
}
