package com.idar.optisaas.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleRequestJsonTest {

    // Replica el ObjectMapper autoconfigurado de Spring Boot, que por defecto
    // ignora propiedades desconocidas en vez de lanzar una excepción.
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void deserializesIsQuotationTrueFromFrontendPayload() throws Exception {
        String json = "{\"clientId\":1,\"items\":[],\"isQuotation\":true,\"parkSale\":false}";
        SaleRequest request = mapper.readValue(json, SaleRequest.class);
        assertTrue(request.isQuotation(), "isQuotation debe quedar en true cuando el JSON envía \"isQuotation\": true");
    }

    @Test
    void defaultsIsQuotationFalseWhenOmitted() throws Exception {
        String json = "{\"clientId\":1,\"items\":[]}";
        SaleRequest request = mapper.readValue(json, SaleRequest.class);
        assertFalse(request.isQuotation());
    }

    @Test
    void deserializesIsQuotationFalseExplicitly() throws Exception {
        String json = "{\"clientId\":1,\"items\":[],\"isQuotation\":false}";
        SaleRequest request = mapper.readValue(json, SaleRequest.class);
        assertFalse(request.isQuotation());
    }
}
