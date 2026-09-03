/**
 * Cash and position accounting for trades produced by the matching engine.
 *
 * <p>This layer deliberately does not fetch prices; valuation belongs to a
 * coordinating service so accounting history cannot be changed by a quote.</p>
 */
package com.tradingsim.portfolio;
