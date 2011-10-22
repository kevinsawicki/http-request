/*
 * Copyright (c) 2011 Kevin Sawicki <kevinsawicki@gmail.com>
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

import static com.github.kevinsawicki.http.HttpRequest.get;
import static com.github.kevinsawicki.http.HttpRequest.post;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.B64Code;
import org.junit.Test;

/**
 * Unit tests of HttpRequest
 */
public class RequestTest extends ServerTestCase {

	/**
	 * Make a GET request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void getEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		HttpRequest request = get(url);
		int code = request.code();
		assertEquals("GET", method.get());
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertEquals("", request.string());
	}

	/**
	 * Make a POST request with an empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		int code = post(url).code();
		assertEquals("POST", method.get());
		assertEquals(HttpURLConnection.HTTP_OK, code);
	}

	/**
	 * Make a POST request with a non-empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postNonEmptyString() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		int code = post(url).body("hello").code();
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertEquals("hello", body.get());
	}

	/**
	 * Make a post with an explicit set of the content length
	 *
	 * @throws Exception
	 */
	@Test
	public void postWithLength() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		final AtomicReference<Integer> length = new AtomicReference<Integer>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				length.set(request.getContentLength());
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		String data = "hello";
		int sent = data.getBytes().length;
		int code = post(url).contentLength(sent).body(data).code();
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertEquals(sent, length.get().intValue());
		assertEquals(data, body.get());
	}

	/**
	 * Make a post of form data
	 *
	 * @throws Exception
	 */
	@Test
	public void postForm() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		Map<String, String> data = new HashMap<String, String>();
		data.put("name", "user");
		data.put("number", "100");
		int code = post(url).form(data).code();
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertTrue(body.get().contains("name=user"));
		assertTrue(body.get().contains("number=100"));
	}

	/**
	 * Make a post in chunked mode
	 *
	 * @throws Exception
	 */
	@Test
	public void chunkPost() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		final AtomicReference<String> encoding = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HttpServletResponse.SC_OK);
				encoding.set(request.getHeader("Transfer-Encoding"));
			}
		});
		String data = "hello";
		int code = post(url).chunk(2).body(data).code();
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertEquals(data, body.get());
		assertEquals("chunked", encoding.get());
	}

	/**
	 * Make a GET request for a non-empty response body
	 *
	 * @throws Exception
	 */
	@Test
	public void getNonEmptyString() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertEquals(HttpURLConnection.HTTP_OK, request.code());
		assertEquals("hello", request.string());
		assertEquals("hello".getBytes().length, request.contentLength());
	}

	/**
	 * Make a GET request with a response that includes a charset parameter
	 *
	 * @throws Exception
	 */
	@Test
	public void getWithResponseCharset() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_OK);
				response.setContentType("text/html; charset=UTF-8");
			}
		});
		HttpRequest request = get(url);
		assertEquals(HttpURLConnection.HTTP_OK, request.code());
		assertEquals("UTF-8", request.charset());
	}

	/**
	 * Make a GET request with basic authentication specified
	 *
	 * @throws Exception
	 */
	@Test
	public void basicAuthentication() throws Exception {
		final AtomicReference<String> user = new AtomicReference<String>();
		final AtomicReference<String> password = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				String auth = request.getHeader("Authentication");
				auth = auth.substring(auth.indexOf(' ') + 1);
				try {
					auth = B64Code.decode(auth, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
				int colon = auth.indexOf(':');
				user.set(auth.substring(0, colon));
				password.set(auth.substring(colon + 1));
				response.setStatus(HttpServletResponse.SC_OK);
			}
		});
		assertEquals(200, get(url).basic("user", "p4ssw0rd").code());
		assertEquals("user", user.get());
		assertEquals("p4ssw0rd", password.get());
	}
}
