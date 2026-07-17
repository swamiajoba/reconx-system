package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
//@AutoConfigureMockMvc(addFilters = false) // skip JwtAuthenticationFilter and other security filters
@Import(SecurityConfig.class)
class TradeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TradeService tradeService;
    @MockBean private TradeMapper mapper; // controller calls mapper.toResponse(saved) directly — must be mocked
    @MockBean private JwtTokenProvider jwtTokenProvider; // ...but its bean still needs to be constructible at context startup

    private TradeRequest validRequest() {
        // Field order matches the current TradeRequest record:
        // (tradeRef, instrumentId, counterpartyId, assetClass, side, quantity, price, tradeDate).
        // tradeRef regex: ^[A-Z]{3}-\d{8}-\d{4}$. Status is NOT a request field — it is set server-side.
        return new TradeRequest(
                "TRD-20260315-9999",
                1L,
                1L,
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now());
    }

    @Test
    @WithMockUser(roles = "TRADER")
//    @WithMockUser(username = "trader", roles = "TRADER")
    void testCreateTrade_authenticated_returns201() throws Exception {
        // Field order matches the current TradeResponse record:
        // (id, tradeRef, instrumentId, instrumentSymbol, counterpartyId, counterpartyName,
        //  assetClass, side, quantity, price, tradeDate, status, createdAt, modifiedAt).
//        Instant now = Instant.now();
//        when(tradeService.create(any())).thenReturn(
//                new TradeResponse(
//                        42L,
//                        "TRD-20260315-9999",
//                        1L,
//                        "SAP.DE",
//                        1L,
//                        "Apex Brokers Inc",
//                        "EQUITY",
//                        "BUY",
//                        new BigDecimal("100.0000"),
//                        new BigDecimal("245.50"),
//                        LocalDate.now(),
//                        "PENDING",
//                        now,
//                        now));

        Instant now = Instant.now();
        Trade savedEntity = new Trade();
        savedEntity.setId(42L);

        TradeResponse response = new TradeResponse(
                42L,
                "TRD-20260315-9999",
                1L,
                "SAP.DE",
                1L,
                "Apex Brokers Inc",
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now(),
                "PENDING",
                now,
                now);

        // Two-arg stub matching service.create(req, actor) exactly
        when(tradeService.create(any(TradeRequest.class), anyString()))
                .thenReturn(savedEntity);

        // mapper.toResponse(saved) must be stubbed separately — controller calls it directly
        when(mapper.toResponse(savedEntity))
                .thenReturn(response);

        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.tradeRef").value("TRD-20260315-9999"));
    }


    @Test
    void testCreateTrade_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @WithMockUser(roles = "VIEWER")
    void testCreateTrade_viewerRole_returns403() throws Exception {
        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}