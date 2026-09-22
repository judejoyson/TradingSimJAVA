package com.tradingsim.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsPrivateApisAndRegistersAUserSession() throws Exception {
        mockMvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/competitive/leaderboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/competitive.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/backtest.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/trade-plan-validation.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/backtests/options"))
                .andExpect(status().isOk());

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Replay Learner",
                                  "email": "learner@example.com",
                                  "password": "correct-horse-battery"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("learner@example.com"))
                .andReturn();

        mockMvc.perform(get("/api/auth/me")
                        .session((MockHttpSession) registration.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Replay Learner"));

        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        mockMvc.perform(get("/api/account").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("NORMAL"))
                .andExpect(jsonPath("$.startingCash").value(100000.00))
                .andExpect(jsonPath("$.nextDepositAt").doesNotExist());

        mockMvc.perform(post("/api/orders")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol": "AAPL", "side": "BUY", "quantity": 1}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/account/mode")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode": "COMPETITIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COMPETITIVE"))
                .andExpect(jsonPath("$.startingCash").value(250000.00))
                .andExpect(jsonPath("$.depositAmount").value(75000.00))
                .andExpect(jsonPath("$.nextDepositAt").exists());

        mockMvc.perform(get("/competitive.html").session(session))
                .andExpect(status().isOk());

        MvcResult competitiveSession = mockMvc.perform(post("/api/competitive/session")
                        .session(session)
                        .with(csrf())
                        .param("symbol", "AAPL")
                        .param("timeframe", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay.candles.length()").value(70))
                .andExpect(jsonPath("$.totalCandles").value(360))
                .andReturn();
        String sessionId = JsonPath.read(
                competitiveSession.getResponse().getContentAsString(),
                "$.sessionId");

        mockMvc.perform(get("/api/competitive/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.session.sessionId").value(sessionId))
                .andExpect(jsonPath("$.session.replay.candles.length()").value(70));

        MvcResult purchase = mockMvc.perform(post("/api/competitive/orders")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "side": "BUY",
                                  "quantity": 1
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.positions[0].symbol").value("AAPL"))
                .andReturn();

        mockMvc.perform(post("/api/competitive/session")
                        .session(session)
                        .with(csrf())
                        .param("symbol", "SPY")
                        .param("timeframe", "1m"))
                .andExpect(status().isBadRequest());

        BigDecimal purchasePrice = new BigDecimal(
                JsonPath.read(purchase.getResponse().getContentAsString(), "$.trade.price")
                        .toString());

        BigDecimal salePrice = purchasePrice;
        for (int index = 0; index < 20 && salePrice.compareTo(purchasePrice) == 0; index++) {
            MvcResult advance = mockMvc.perform(post(
                                    "/api/competitive/session/{sessionId}/advance",
                                    sessionId)
                            .session(session)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();
            salePrice = new BigDecimal(
                    JsonPath.read(advance.getResponse().getContentAsString(), "$.candle.close")
                            .toString());
        }
        assertNotEquals(0, salePrice.compareTo(purchasePrice));

        MvcResult sale = mockMvc.perform(post("/api/competitive/orders")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "side": "SELL",
                                  "quantity": 1
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.account.positions").isEmpty())
                .andReturn();
        BigDecimal realized = new BigDecimal(
                JsonPath.read(sale.getResponse().getContentAsString(),
                        "$.account.realizedProfitLoss").toString());
        assertNotEquals(0, realized.compareTo(BigDecimal.ZERO));

        mockMvc.perform(get("/api/competitive/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.completed").value(true))
                .andExpect(jsonPath("$.session.entryQuantity").value(1));

        mockMvc.perform(post("/api/competitive/orders")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "side": "BUY",
                                  "quantity": 1
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/account/mode")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode": "NORMAL"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/account/deposit")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/competitive/leaderboard")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Replay Learner"))
                .andExpect(jsonPath("$[0].returnPercent").isNumber())
                .andExpect(jsonPath("$[0].sharpeRatio").isNumber())
                .andExpect(jsonPath("$[0].consistencyPercent").isNumber());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "learner@example.com")
                        .param("password", "correct-horse-battery"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/backtest.html"));
    }

    @Test
    void sendsBrowserSecurityHeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Permissions-Policy"));
    }

}
