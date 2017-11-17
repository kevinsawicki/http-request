/*
 * Copyright (c) 2012 Kevin Sawicki <kevinsawicki@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.uvasoftware.http;

import com.uvasoftware.http.internal.HttpUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests of URL encoding done by {@link HttpRequest}
 */
public class EncodeTest {

  /**
   * Verify encoding of URLs
   */
  @Test
  public void encode() {
    assertEquals("http://google.com", HttpUtils.encode("http://google.com"));

    assertEquals("https://google.com", HttpUtils.encode("https://google.com"));

    assertEquals("http://google.com/a",
        HttpUtils.encode("http://google.com/a"));

    assertEquals("http://google.com/a/",
        HttpUtils.encode("http://google.com/a/"));

    assertEquals("http://google.com/a/b",
        HttpUtils.encode("http://google.com/a/b"));

    assertEquals("http://google.com/a?",
        HttpUtils.encode("http://google.com/a?"));

    assertEquals("http://google.com/a?b=c",
        HttpUtils.encode("http://google.com/a?b=c"));

    assertEquals("http://google.com/a?b=c%20d",
        HttpUtils.encode("http://google.com/a?b=c d"));

    assertEquals("http://google.com/a%20b",
        HttpUtils.encode("http://google.com/a b"));

    assertEquals("http://google.com/a.b",
        HttpUtils.encode("http://google.com/a.b"));

    assertEquals("http://google.com/%E2%9C%93?a=b",
        HttpUtils.encode("http://google.com/\u2713?a=b"));

    assertEquals("http://google.com/a%5Eb",
        HttpUtils.encode("http://google.com/a^b"));

    assertEquals("http://google.com/%25",
        HttpUtils.encode("http://google.com/%"));

    assertEquals("http://google.com/a.b?c=d.e",
        HttpUtils.encode("http://google.com/a.b?c=d.e"));

    assertEquals("http://google.com/a.b?c=d/e",
        HttpUtils.encode("http://google.com/a.b?c=d/e"));

    assertEquals("http://google.com/a?%E2%98%91",
        HttpUtils.encode("http://google.com/a?\u2611"));

    assertEquals("http://google.com/a?b=%E2%98%90",
        HttpUtils.encode("http://google.com/a?b=\u2610"));

    assertEquals("http://google.com/a?b=c%2Bd&e=f%2Bg",
        HttpUtils.encode("http://google.com/a?b=c+d&e=f+g"));

    assertEquals("http://google.com/+",
        HttpUtils.encode("http://google.com/+"));

    assertEquals("http://google.com/+?a=b%2Bc",
        HttpUtils.encode("http://google.com/+?a=b+c"));
  }

  /**
   * Encoding malformed URI
   */
  @Test(expected = HttpRequestException.class)
  public void encodeMalformedUri() {
    HttpUtils.encode("\\m/");
  }
}
