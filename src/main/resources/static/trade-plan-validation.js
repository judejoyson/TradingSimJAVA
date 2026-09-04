"use strict";

window.TradePlanValidation = {
    error(direction, plan) {
        if (plan.entry == null || plan.stop == null || plan.target == null) {
            return null;
        }
        if (direction === "SHORT") {
            if (plan.stop <= plan.entry) {
                return "Invalid setup: For SHORT: move Stop-Loss above Entry.";
            }
            if (plan.target >= plan.entry) {
                return "Invalid setup: For SHORT: move Take-Profit below Entry.";
            }
            return null;
        }
        if (plan.stop >= plan.entry) {
            return "Invalid setup: For LONG: move Stop-Loss below Entry.";
        }
        if (plan.target <= plan.entry) {
            return "Invalid setup: For LONG: move Take-Profit above Entry.";
        }
        return null;
    }
};
