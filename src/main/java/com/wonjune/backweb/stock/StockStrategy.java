package com.wonjune.backweb.stock;

/**
 * Three ways to take stock off the shelf, kept side by side so the load test can compare
 * them on identical traffic. ATOMIC is the production default; NONE exists to reproduce
 * the oversell it prevents.
 */
public enum StockStrategy {

	/** Read, check, write. Two requests can both read the same value and both succeed. */
	NONE,

	/** SELECT ... FOR UPDATE. Correct, but same-product checkouts serialise on the lock. */
	PESSIMISTIC,

	/** Conditional UPDATE evaluated inside the database. Correct with the shortest lock hold. */
	ATOMIC

}
