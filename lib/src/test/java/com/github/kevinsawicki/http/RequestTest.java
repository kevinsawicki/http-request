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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.kevinsawicki.http.HttpRequest.RequestException;

import java.io.BufferedReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Arrays;
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
	 * Create requerst with malformed URL
	 */
	@Test(expected = RequestException.class)
	public void malformedStringUrl() {
		HttpRequest.get("\\m/");
	}

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
		assertNotNull(request.getConnection());
		int code = request.code();
		assertTrue(request.ok());
		assertFalse(request.created());
		assertFalse(request.badRequest());
		assertFalse(request.serverError());
		assertFalse(request.notFound());
		assertEquals("GET", method.get());
		assertEquals("OK", request.message());
		assertEquals(HttpURLConnection.HTTP_OK, code);
		assertEquals("", request.body());
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
				response.setStatus(HttpServletResponse.SC_CREATED);
			}
		});
		HttpRequest request = post(url);
		int code = request.code();
		assertEquals("POST", method.get());
		assertFalse(request.ok());
		assertTrue(request.created());
		assertEquals(HttpURLConnection.HTTP_CREATED, code);
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
		int code = post(url).send("hello").code();
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
		int code = post(url).contentLength(sent).send(data).code();
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
		int code = post(url).chunk(2).send(data).code();
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
		assertEquals("hello", request.body());
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
	 * Make a GET request with a response that includes a charset parameter
	 *
	 * @throws Exception
	 */
	@Test
	public void getWithResponseCharsetAsSecondParam() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_OK);
				response.setContentType("text/html; param1=val1; charset=UTF-8");
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
		assertTrue(get(url).basic("user", "p4ssw0rd").ok());
		assertEquals("user", user.get());
		assertEquals("p4ssw0rd", password.get());
	}

	/**
	 * Make a GET and get response as a input stream reader
	 *
	 * @throws Exception
	 */
	@Test
	public void getReader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		BufferedReader reader = new BufferedReader(request.reader());
		assertEquals("hello", reader.readLine());
	}

	/**
	 * Make a GET and get response body as byte array
	 *
	 * @throws Exception
	 */
	@Test
	public void getBytes() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertTrue(Arrays.equals("hello".getBytes(), request.bytes()));
	}

	/**
	 * Make a GET request that returns an error string
	 *
	 * @throws Exception
	 */
	@Test
	public void getError() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				write("error");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.notFound());
		assertEquals("error", request.errorString());
	}
}
