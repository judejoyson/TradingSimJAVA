/**
 * Date-range historical strategy backtesting, metrics, and REST endpoints.
 *
 * <p>Signals use each candle's close and execute at the following candle's
 * open to avoid using information before it would have been available.</p>
 */
package com.tradingsim.backtest;
