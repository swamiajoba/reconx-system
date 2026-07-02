package com.dbtraining.reconx.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquityTradeTest {

    @Test
    void builder_buildsWhenAllRequiredPresent() {
        EquityTrade trade = EquityTrade.builder()
                .tradeRef(TradeRef.of("EQU-20260603-0001"))
                .instrumentSymbol("SAP.DE")
                .quantity(new BigDecimal("1000"))
                .price(new BigDecimal("125.50"))
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();

        assertThat(trade.tradeRef().value()).isEqualTo("EQU-20260603-0001");
        assertThat(trade.notional().amount()).isEqualByComparingTo("125500.00");
        assertThat(trade.assetClass()).isEqualTo(TradeType.AssetClass.EQUITY);
    }

    @Test
    void builder_missingPrice_throws() {
        assertThatThrownBy(() -> EquityTrade.builder()
                .tradeRef(TradeRef.of("EQU-20260603-0001"))
                .instrumentSymbol("SAP.DE")
                .quantity(new BigDecimal("1000"))
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("price");
    }

    @Test
    void equality_byTradeRef() {
        EquityTrade a = sampleEquity("EQU-20260603-0001");
        EquityTrade b = sampleEquity("EQU-20260603-0001");
        EquityTrade c = sampleEquity("EQU-20260603-0002");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    private EquityTrade sampleEquity(String ref) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("100"))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L).build();
    }
}
