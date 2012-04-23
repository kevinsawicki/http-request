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
package com.github.kevinsawicki.http;

import static org.junit.Assert.assertEquals;

import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;

import org.junit.Test;

/**
 * Unit tests of URL encoding done by {@link HttpRequest}
 */
public class EncodeTest {

	/**
	 * Verify encoding of URLs
	 */
	@Test
	public void encode() {
		assertEquals("http://google.com",
				HttpRequest.encode("http://google.com"));

		assertEquals("https://google.com",
				HttpRequest.encode("https://google.com"));

		assertEquals("http://google.com/a",
				HttpRequest.encode("http://google.com/a"));

		assertEquals("http://google.com/a/",
				HttpRequest.encode("http://google.com/a/"));

		assertEquals("http://google.com/a/b",
				HttpRequest.encode("http://google.com/a/b"));

		assertEquals("http://google.com/a?",
				HttpRequest.encode("http://google.com/a?"));

		assertEquals("http://google.com/a?b=c",
				HttpRequest.encode("http://google.com/a?b=c"));

		assertEquals("http://google.com/a?b=c%20d",
				HttpRequest.encode("http://google.com/a?b=c d"));

		assertEquals("http://google.com/a%20b",
				HttpRequest.encode("http://google.com/a b"));

		assertEquals("http://google.com/a.b",
				HttpRequest.encode("http://google.com/a.b"));

		assertEquals("http://google.com/%E2%9C%93?a=b",
				HttpRequest.encode("http://google.com/\u2713?a=b"));

		assertEquals("http://google.com/a%5Eb",
				HttpRequest.encode("http://google.com/a^b"));

		assertEquals("http://google.com/%25",
				HttpRequest.encode("http://google.com/%"));

		assertEquals("http://google.com/a.b?c=d.e",
				HttpRequest.encode("http://google.com/a.b?c=d.e"));

		assertEquals("http://google.com/a.b?c=d/e",
				HttpRequest.encode("http://google.com/a.b?c=d/e"));

		assertEquals("http://google.com/a?%E2%98%91",
				HttpRequest.encode("http://google.com/a?\u2611"));

		assertEquals("http://google.com/a?b=%E2%98%90",
				HttpRequest.encode("http://google.com/a?b=\u2610"));
	}

	/**
	 * Encoding malformed URI
	 */
	@Test(expected = HttpRequestException.class)
	public void encodeMalformedUri() {
		HttpRequest.encode("\\m/");
	}
}
