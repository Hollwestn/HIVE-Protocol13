package ru.hive13.model;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
public final class Json {
    public static final ObjectMapper MAPPER=JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private Json(){}
    public static <T> T copy(T value,Class<T> type){return MAPPER.convertValue(value,type);}
}
