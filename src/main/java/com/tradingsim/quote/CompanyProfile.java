package com.tradingsim.quote;

import java.math.BigDecimal;

public record CompanyProfile(
        String name,
        String ticker,
        String exchange,
        String industry,
        String country,
        String currency,
        String ipoDate,
        String logoUrl,
        String websiteUrl,
        BigDecimal marketCapitalization,
        BigDecimal sharesOutstanding) {
}
