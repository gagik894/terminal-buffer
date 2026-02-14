package com.gagik.terminal.util

/**
 * Shared validation utilities for terminal components.
 * Centralizes common validation logic to ensure DRY and consistency.
 */
object Validations {

    /**
     * Requires that the given value is positive (> 0).
     * @param value The value to check
     * @param name The name of the parameter for error messages
     * @throws IllegalArgumentException if value is not > 0
     */
    fun requirePositive(value: Int, name: String) {
        require(value > 0) { "$name must be > 0, was $value" }
    }

    /**
     * Requires that the given value is non-negative (>= 0).
     * @param value The value to check
     * @param name The name of the parameter for error messages
     * @throws IllegalArgumentException if value is negative
     */
    fun requireNonNegative(value: Int, name: String) {
        require(value >= 0) { "$name must be >= 0, was $value" }
    }

    /**
     * Checks if an index is within bounds [0, size).
     * @param index The index to check
     * @param size The exclusive upper bound
     * @return true if index is in valid range
     */
    fun isInBounds(index: Int, size: Int): Boolean = index in 0 until size
}

