package com.tradingsim.account;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountModeRequest(@NotNull AccountMode mode) {
}
