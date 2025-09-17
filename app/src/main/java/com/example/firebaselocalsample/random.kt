package com.example.firebaselocalsample

import kotlin.random.Random

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * The characters returned are taken from the set of characters comprising of the 10 numeric digits
 * and the 26 lowercase English characters.
 *
 * @param length the number of random characters to generate and include in the returned string;
 *   must be greater than or equal to zero.
 * @return a string containing the given number of random alphanumeric characters.
 * @hide
 */
@Suppress("unused")
fun Random.nextAlphanumericString(length: Int): String {
  require(length >= 0) { "invalid length: $length" }
  return (0 until length).map { ALPHANUMERIC_ALPHABET.random(this) }.joinToString(separator = "")
}

// The set of characters comprising of the 10 numeric digits and the 26 lowercase letters of the
// English alphabet with some characters removed that can look similar in different fonts, like
// '1', 'l', and 'i'.
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnpqrstvwxyz"
