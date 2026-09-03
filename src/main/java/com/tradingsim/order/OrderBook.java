package com.tradingsim.order;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Holds the two price-priority queues for one symbol.
 *
 * <p>Bids sort highest price first, asks lowest price first, and sequence
 * breaks equal-price ties in favor of the oldest order.</p>
 */
public final class OrderBook {
    private static final Comparator<BookOrder> BID_PRIORITY =
            Comparator.<BookOrder, java.math.BigDecimal>comparing(order -> order.order().limitPrice())
                    .reversed()
                    .thenComparingLong(order -> order.order().sequence());
    private static final Comparator<BookOrder> ASK_PRIORITY =
            Comparator.<BookOrder, java.math.BigDecimal>comparing(order -> order.order().limitPrice())
                    .thenComparingLong(order -> order.order().sequence());

    private final PriorityQueue<BookOrder> bids = new PriorityQueue<>(BID_PRIORITY);
    private final PriorityQueue<BookOrder> asks = new PriorityQueue<>(ASK_PRIORITY);

    PriorityQueue<BookOrder> bids() {
        return bids;
    }

    PriorityQueue<BookOrder> asks() {
        return asks;
    }

    static final class BookOrder {
        private final Order order;
        private long remainingQuantity;

        BookOrder(Order order, long remainingQuantity) {
            this.order = order;
            this.remainingQuantity = remainingQuantity;
        }

        Order order() {
            return order;
        }

        long remainingQuantity() {
            return remainingQuantity;
        }

        void fill(long quantity) {
            remainingQuantity -= quantity;
        }
    }
}
