package com.tradingsim.replay;

/**
 * Market-specific assumptions used by the browser execution model.
 *
 * @param spreadBps full bid/ask distance in basis points
 * @param slippageBps adverse market and stop-order adjustment in basis points
 * @param feeRateBps transaction fee charged on each fill's notional value
 * @param maxVolumeParticipationPercent maximum candle volume an entry may fill
 */
public record ExecutionProfile(
        double spreadBps,
        double slippageBps,
        double feeRateBps,
        double maxVolumeParticipationPercent) {
}
