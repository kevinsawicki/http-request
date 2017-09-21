package com.uvasoftware.http.internal;

import java.io.UnsupportedEncodingException;

/**
 * <p>
 * Encodes and decodes to and from Base64 notation.
 * </p>
 * <p>
 * I am placing this code in the Public Domain. Do with it as you will. This
 * software comes with no guarantees or warranties but with plenty of
 * well-wishing instead! Please visit <a
 * href="http://iharder.net/base64">http://iharder.net/base64</a> periodically
 * to check for updates or to contribute improvements.
 * </p>
 *
 * @author Robert Harder
 * @author rob@iharder.net
 * @version 2.3.7
 */
public class Base64 {

  /**
   * The equals sign (=) as a byte.
   */
  private final static byte EQUALS_SIGN = (byte) '=';

  /**
   * Preferred encoding.
   */
  private final static String PREFERRED_ENCODING = "US-ASCII";

  /**
   * The 64 valid Base64 values.
   */
  private final static byte[] _STANDARD_ALPHABET = {(byte) 'A', (byte) 'B',
    (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G', (byte) 'H',
    (byte) 'I', (byte) 'J', (byte) 'K', (byte) 'L', (byte) 'M', (byte) 'N',
    (byte) 'O', (byte) 'P', (byte) 'Q', (byte) 'R', (byte) 'S', (byte) 'T',
    (byte) 'U', (byte) 'V', (byte) 'W', (byte) 'X', (byte) 'Y', (byte) 'Z',
    (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f',
    (byte) 'g', (byte) 'h', (byte) 'i', (byte) 'j', (byte) 'k', (byte) 'l',
    (byte) 'm', (byte) 'n', (byte) 'o', (byte) 'p', (byte) 'q', (byte) 'r',
    (byte) 's', (byte) 't', (byte) 'u', (byte) 'v', (byte) 'w', (byte) 'x',
    (byte) 'y', (byte) 'z', (byte) '0', (byte) '1', (byte) '2', (byte) '3',
    (byte) '4', (byte) '5', (byte) '6', (byte) '7', (byte) '8', (byte) '9',
    (byte) '+', (byte) '/'};

  /**
   * Defeats instantiation.
   */
  private Base64() {
  }

  /**
   * <p>
   * Encodes up to three bytes of the array <var>source</var> and writes the
   * resulting four Base64 bytes to <var>destination</var>. The source and
   * destination arrays can be manipulated anywhere along their length by
   * specifying <var>srcOffset</var> and <var>destOffset</var>. This method
   * does not check to make sure your arrays are large enough to accomodate
   * <var>srcOffset</var> + 3 for the <var>source</var> array or
   * <var>destOffset</var> + 4 for the <var>destination</var> array. The
   * actual number of significant bytes in your array is given by
   * <var>numSigBytes</var>.
   * </p>
   * <p>
   * This is the lowest level of the encoding methods with all possible
   * parameters.
   * </p>
   *
   * @param source      the array to convert
   * @param srcOffset   the index where conversion begins
   * @param numSigBytes the number of significant bytes in your array
   * @param destination the array to hold the conversion
   * @param destOffset  the index where output will be put
   * @return the <var>destination</var> array
   * @since 1.3
   */
  private static byte[] encode3to4(byte[] source, int srcOffset,
                                   int numSigBytes, byte[] destination, int destOffset) {

    byte[] ALPHABET = _STANDARD_ALPHABET;

    int inBuff = (numSigBytes > 0 ? ((source[srcOffset] << 24) >>> 8) : 0)
      | (numSigBytes > 1 ? ((source[srcOffset + 1] << 24) >>> 16) : 0)
      | (numSigBytes > 2 ? ((source[srcOffset + 2] << 24) >>> 24) : 0);

    switch (numSigBytes) {
      case 3:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
        destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 0x3f];
        destination[destOffset + 3] = ALPHABET[(inBuff) & 0x3f];
        return destination;

      case 2:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
        destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 0x3f];
        destination[destOffset + 3] = EQUALS_SIGN;
        return destination;

      case 1:
        destination[destOffset] = ALPHABET[(inBuff >>> 18)];
        destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
        destination[destOffset + 2] = EQUALS_SIGN;
        destination[destOffset + 3] = EQUALS_SIGN;
        return destination;

      default:
        return destination;
    }
  }

  /**
   * Encode string as a byte array in Base64 annotation.
   *
   * @param string
   * @return The Base64-encoded data as a string
   */
  public static String encode(String string) {
    byte[] bytes;
    try {
      bytes = string.getBytes(PREFERRED_ENCODING);
    } catch (UnsupportedEncodingException e) {
      bytes = string.getBytes();
    }
    return encodeBytes(bytes);
  }

  /**
   * Encodes a byte array into Base64 notation.
   *
   * @param source The data to convert
   * @return The Base64-encoded data as a String
   * @throws NullPointerException     if source array is null
   * @throws IllegalArgumentException if source array, offset, or length are invalid
   * @since 2.0
   */
  private static String encodeBytes(byte[] source) {
    return encodeBytes(source, 0, source.length);
  }

  /**
   * Encodes a byte array into Base64 notation.
   *
   * @param source The data to convert
   * @param off    Offset in array where conversion should begin
   * @param len    Length of data to convert
   * @return The Base64-encoded data as a String
   * @throws NullPointerException     if source array is null
   * @throws IllegalArgumentException if source array, offset, or length are invalid
   * @since 2.0
   */
  private static String encodeBytes(byte[] source, int off, int len) {
    byte[] encoded = encodeBytesToBytes(source, off, len);
    try {
      return new String(encoded, PREFERRED_ENCODING);
    } catch (UnsupportedEncodingException uue) {
      return new String(encoded);
    }
  }

  /**
   * Similar to {@link #encodeBytes(byte[], int, int)} but returns a byte
   * array instead of instantiating a String. This is more efficient if you're
   * working with I/O streams and have large data sets to encode.
   *
   * @param source The data to convert
   * @param off    Offset in array where conversion should begin
   * @param len    Length of data to convert
   * @return The Base64-encoded data as a String if there is an error
   * @throws NullPointerException     if source array is null
   * @throws IllegalArgumentException if source array, offset, or length are invalid
   * @since 2.3.1
   */
  private static byte[] encodeBytesToBytes(byte[] source, int off, int len) {

    if (source == null)
      throw new NullPointerException("Cannot serialize a null array.");

    if (off < 0)
      throw new IllegalArgumentException("Cannot have negative offset: "
        + off);

    if (len < 0)
      throw new IllegalArgumentException("Cannot have length offset: " + len);

    if (off + len > source.length)
      throw new IllegalArgumentException(
        String
          .format(
            "Cannot have offset of %d and length of %d with array of length %d",
            off, len, source.length));

    // Bytes needed for actual encoding
    int encLen = (len / 3) * 4 + (len % 3 > 0 ? 4 : 0);

    byte[] outBuff = new byte[encLen];

    int d = 0;
    int e = 0;
    int len2 = len - 2;
    for (; d < len2; d += 3, e += 4)
      encode3to4(source, d + off, 3, outBuff, e);

    if (d < len) {
      encode3to4(source, d + off, len - d, outBuff, e);
      e += 4;
    }

    if (e <= outBuff.length - 1) {
      byte[] finalOut = new byte[e];
      System.arraycopy(outBuff, 0, finalOut, 0, e);
      return finalOut;
    } else
      return outBuff;
  }
}
