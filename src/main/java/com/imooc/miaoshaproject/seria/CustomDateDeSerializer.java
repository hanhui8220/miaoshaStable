package com.imooc.miaoshaproject.seria;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;

public class CustomDateDeSerializer extends JsonDeserializer<DateTime> {

    @Override
    public DateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {

        String date = jsonParser.readValueAs(String.class);
        DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-mm-dd HH:MM:ss");
        return DateTime.parse(date,dateFormat);
    }
}
