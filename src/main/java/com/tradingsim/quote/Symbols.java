package com.tradingsim.quote;

import com.tradingsim.web.BadRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Symbols {
    public static final String VALID_SYMBOL_PATTERN = "[A-Za-z][A-Za-z0-9.-]{0,9}";
    private static final Pattern VALID_SYMBOL = Pattern.compile(VALID_SYMBOL_PATTERN);

    private Symbols() {
    }

    public static String normalize(String symbol) {
        if (symbol == null || !VALID_SYMBOL.matcher(symbol.trim()).matches()) {
            throw new BadRequestException(
                    "A symbol must start with a letter and contain at most 10 letters, numbers, dots, or dashes.");
        }
        return symbol.trim().toUpperCase(Locale.US);
    }
}
