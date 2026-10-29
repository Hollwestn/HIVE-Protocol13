package ru.hive13.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BootstrapTest {
    @Test void supportsRussianSerialization() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("Объект-13", mapper.readTree(mapper.writeValueAsString(java.util.Map.of("name","Объект-13"))).get("name").asText());
    }
}
