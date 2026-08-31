package com.tradingsim.order;

import com.tradingsim.order.OrderBook.BookOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public final class MatchingEngine {
    private final OrderBookManager orderBooks;

    public MatchingEngine(OrderBookManager orderBooks) {
        this.orderBooks = orderBooks;
    }

    public List<Trade> submit(Order incoming) {
        OrderBook book = orderBooks.bookFor(incoming.symbol());
        PriorityQueue<BookOrder> opposite = incoming.side() == Side.BUY ? book.asks() : book.bids();
        long remaining = incoming.quantity();
        List<Trade> trades = new ArrayList<>();

        while (remaining > 0 && !opposite.isEmpty() && crosses(incoming, opposite.peek().order())) {
            BookOrder resting = opposite.peek();
            long fillQuantity = Math.min(remaining, resting.remainingQuantity());
            trades.add(toTrade(incoming, resting.order(), fillQuantity));
            remaining -= fillQuantity;
            resting.fill(fillQuantity);
            if (resting.remainingQuantity() == 0) {
                opposite.remove();
            }
        }

        if (remaining > 0 && incoming.type() == OrderType.LIMIT) {
            BookOrder resting = new BookOrder(incoming, remaining);
            (incoming.side() == Side.BUY ? book.bids() : book.asks()).add(resting);
        }
        return List.copyOf(trades);
    }

    private boolean crosses(Order incoming, Order resting) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        return incoming.side() == Side.BUY
                ? incoming.limitPrice().compareTo(resting.limitPrice()) >= 0
                : incoming.limitPrice().compareTo(resting.limitPrice()) <= 0;
    }

    private Trade toTrade(Order incoming, Order resting, long quantity) {
        Order buy = incoming.side() == Side.BUY ? incoming : resting;
        Order sell = incoming.side() == Side.SELL ? incoming : resting;
        return new Trade(
                buy.id(),
                sell.id(),
                buy.accountId(),
                sell.accountId(),
                incoming.symbol(),
                quantity,
                resting.limitPrice(),
                incoming.submittedAt());
    }
}
