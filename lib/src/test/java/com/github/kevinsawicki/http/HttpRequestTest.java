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

import static com.github.kevinsawicki.http.HttpRequest.CHARSET_UTF8;
import static com.github.kevinsawicki.http.HttpRequest.delete;
import static com.github.kevinsawicki.http.HttpRequest.encode;
import static com.github.kevinsawicki.http.HttpRequest.get;
import static com.github.kevinsawicki.http.HttpRequest.head;
import static com.github.kevinsawicki.http.HttpRequest.options;
import static com.github.kevinsawicki.http.HttpRequest.post;
import static com.github.kevinsawicki.http.HttpRequest.put;
import static com.github.kevinsawicki.http.HttpRequest.trace;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.B64Code;
import org.junit.Test;

/**
 * Unit tests of {@link HttpRequest}
 */
public class HttpRequestTest extends ServerTestCase {

	/**
	 * Create request with malformed URL
	 */
	@Test(expected = HttpRequestException.class)
	public void malformedStringUrl() {
		get("\\m/");
	}

	/**
	 * Create request with malformed URL
	 */
	@Test
	public void malformedStringUrlCause() {
		try {
			delete("\\m/");
			fail("Exception not thrown");
		} catch (HttpRequestException e) {
			assertNotNull(e.getCause());
		}
	}

	/**
	 * Set request buffer size to negative value
	 */
	@Test(expected = IllegalArgumentException.class)
	public void negativeBufferSize() {
		get("http://localhost").bufferSize(-1);
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
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(url);
		assertNotNull(request.getConnection());
		assertEquals(30000, request.readTimeout(30000).getConnection()
				.getReadTimeout());
		assertEquals(50000, request.connectTimeout(50000).getConnection()
				.getConnectTimeout());
		assertEquals(2500, request.bufferSize(2500).bufferSize());
		assertFalse(request.ignoreCloseExceptions(false)
				.ignoreCloseExceptions());
		assertFalse(request.useCaches(false).getConnection().getUseCaches());
		int code = request.code();
		assertTrue(request.ok());
		assertFalse(request.created());
		assertFalse(request.badRequest());
		assertFalse(request.serverError());
		assertFalse(request.notFound());
		assertFalse(request.notModified());
		assertEquals("GET", method.get());
		assertEquals("OK", request.message());
		assertEquals(HTTP_OK, code);
		assertEquals("", request.body());
		assertNotNull(request.toString());
		assertFalse(request.toString().length() == 0);
		assertEquals(request, request.disconnect());
	}

	/**
	 * Make a GET request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void getUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(new URL(url));
		assertNotNull(request.getConnection());
		int code = request.code();
		assertTrue(request.ok());
		assertFalse(request.created());
		assertFalse(request.badRequest());
		assertFalse(request.serverError());
		assertFalse(request.notFound());
		assertEquals("GET", method.get());
		assertEquals("OK", request.message());
		assertEquals(HTTP_OK, code);
		assertEquals("", request.body());
	}

	/**
	 * Make a GET request with a URL that needs encoding
	 *
	 * @throws Exception
	 */
	@Test
	public void getUrlEncodedWithSpace() throws Exception {
		String unencoded = "/a resource";
		final AtomicReference<String> path = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				path.set(request.getPathInfo());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(encode(url + unencoded));
		assertTrue(request.ok());
		assertEquals(unencoded, path.get());
	}

	/**
	 * Make a GET request with a URL that needs encoding
	 *
	 * @throws Exception
	 */
	@Test
	public void getUrlEncodedWithUnicode() throws Exception {
		String unencoded = "/\u00DF";
		final AtomicReference<String> path = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				path.set(request.getPathInfo());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(encode(url + unencoded));
		assertTrue(request.ok());
		assertEquals(unencoded, path.get());
	}

	/**
	 * Make a GET request with a URL that needs encoding
	 *
	 * @throws Exception
	 */
	@Test
	public void getUrlEncodedWithPercent() throws Exception {
		String unencoded = "/%";
		final AtomicReference<String> path = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				path.set(request.getPathInfo());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(encode(url + unencoded));
		assertTrue(request.ok());
		assertEquals(unencoded, path.get());
	}

	/**
	 * Make a DELETE request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void deleteEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = delete(url);
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("DELETE", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a DELETE request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void deleteUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = delete(new URL(url));
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("DELETE", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make an OPTIONS request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void optionsEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = options(url);
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("OPTIONS", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make an OPTIONS request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void optionsUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = options(new URL(url));
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("OPTIONS", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a HEAD request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void headEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = head(url);
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("HEAD", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a HEAD request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void headUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = head(new URL(url));
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("HEAD", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a PUT request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void putEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = put(url);
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("PUT", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a PUT request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void putUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = put(new URL(url));
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("PUT", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a PUT request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void traceEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = trace(url);
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("TRACE", method.get());
		assertEquals("", request.body());
	}

	/**
	 * Make a TRACE request with an empty body response
	 *
	 * @throws Exception
	 */
	@Test
	public void traceUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = trace(new URL(url));
		assertNotNull(request.getConnection());
		assertTrue(request.ok());
		assertFalse(request.notFound());
		assertEquals("TRACE", method.get());
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
				response.setStatus(HTTP_CREATED);
			}
		});
		HttpRequest request = post(url);
		int code = request.code();
		assertEquals("POST", method.get());
		assertFalse(request.ok());
		assertTrue(request.created());
		assertEquals(HTTP_CREATED, code);
	}

	/**
	 * Make a POST request with an empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postUrlEmpty() throws Exception {
		final AtomicReference<String> method = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				method.set(request.getMethod());
				response.setStatus(HTTP_CREATED);
			}
		});
		HttpRequest request = post(new URL(url));
		int code = request.code();
		assertEquals("POST", method.get());
		assertFalse(request.ok());
		assertTrue(request.created());
		assertEquals(HTTP_CREATED, code);
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
				response.setStatus(HTTP_OK);
			}
		});
		int code = post(url).send("hello").code();
		assertEquals(HTTP_OK, code);
		assertEquals("hello", body.get());
	}

	/**
	 * Make a POST request with a non-empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postNonEmptyFile() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		File file = File.createTempFile("post", ".txt");
		new FileWriter(file).append("hello").close();
		int code = post(url).send(file).code();
		assertEquals(HTTP_OK, code);
		assertEquals("hello", body.get());
	}

	/**
	 * Make a POST request with multiple files in the body
	 *
	 * @throws Exception
	 */
	@Test
	public void postMultipleFiles() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});

		File file1 = File.createTempFile("post", ".txt");
		new FileWriter(file1).append("hello").close();

		File file2 = File.createTempFile("post", ".txt");
		new FileWriter(file2).append(" world").close();

		int code = post(url).send(file1).send(file2).code();
		assertEquals(HTTP_OK, code);
		assertEquals("hello world", body.get());
	}

	/**
	 * Make a POST request with a non-empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postNonEmptyReader() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		File file = File.createTempFile("post", ".txt");
		new FileWriter(file).append("hello").close();
		int code = post(url).send(new FileReader(file)).code();
		assertEquals(HTTP_OK, code);
		assertEquals("hello", body.get());
	}

	/**
	 * Make a POST request with a non-empty request body
	 *
	 * @throws Exception
	 */
	@Test
	public void postNonEmptyByteArray() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		byte[] bytes = "hello".getBytes(CHARSET_UTF8);
		int code = post(url).contentLength(Integer.toString(bytes.length))
				.send(bytes).code();
		assertEquals(HTTP_OK, code);
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
				response.setStatus(HTTP_OK);
			}
		});
		String data = "hello";
		int sent = data.getBytes().length;
		int code = post(url).contentLength(sent).send(data).code();
		assertEquals(HTTP_OK, code);
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
				response.setStatus(HTTP_OK);
			}
		});
		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("name", "user");
		data.put("number", "100");
		int code = post(url).form(data).form("zip", "12345").code();
		assertEquals(HTTP_OK, code);
		assertEquals("name=user&number=100&zip=12345", body.get());
	}

	/**
	 * Make a post with an empty form data map
	 *
	 * @throws Exception
	 */
	@Test
	public void postEmptyForm() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		int code = post(url).form(new HashMap<String, String>()).code();
		assertEquals(HTTP_OK, code);
		assertEquals("", body.get());
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
				response.setStatus(HTTP_OK);
				encoding.set(request.getHeader("Transfer-Encoding"));
			}
		});
		String data = "hello";
		int code = post(url).chunk(2).send(data).code();
		assertEquals(HTTP_OK, code);
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
				response.setStatus(HTTP_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertEquals(HTTP_OK, request.code());
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
				response.setStatus(HTTP_OK);
				response.setContentType("text/html; charset=UTF-8");
			}
		});
		HttpRequest request = get(url);
		assertEquals(HTTP_OK, request.code());
		assertEquals(CHARSET_UTF8, request.charset());
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
				response.setStatus(HTTP_OK);
				response.setContentType("text/html; param1=val1; charset=UTF-8");
			}
		});
		HttpRequest request = get(url);
		assertEquals(HTTP_OK, request.code());
		assertEquals(CHARSET_UTF8, request.charset());
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
				String auth = request.getHeader("Authorization");
				auth = auth.substring(auth.indexOf(' ') + 1);
				try {
					auth = B64Code.decode(auth, CHARSET_UTF8);
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
				int colon = auth.indexOf(':');
				user.set(auth.substring(0, colon));
				password.set(auth.substring(colon + 1));
				response.setStatus(HTTP_OK);
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
				response.setStatus(HTTP_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		BufferedReader reader = new BufferedReader(request.reader());
		assertEquals("hello", reader.readLine());
		reader.close();
	}

	/**
	 * Make a POST and send request using a writer
	 *
	 * @throws Exception
	 */
	@Test
	public void sendWithWriter() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});

		HttpRequest request = post(url);
		request.writer().append("hello").close();
		assertTrue(request.ok());
		assertEquals("hello", body.get());
	}

	/**
	 * Make a GET and get response as a buffered reader
	 *
	 * @throws Exception
	 */
	@Test
	public void getBufferedReader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		BufferedReader reader = request.bufferedReader();
		assertEquals("hello", reader.readLine());
		reader.close();
	}

	/**
	 * Make a GET and get response as a input stream reader
	 *
	 * @throws Exception
	 */
	@Test
	public void getReaderWithCharset() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				write("hello");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		BufferedReader reader = new BufferedReader(request.reader(CHARSET_UTF8));
		assertEquals("hello", reader.readLine());
		reader.close();
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
				response.setStatus(HTTP_OK);
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
		assertEquals("error", request.body());
	}

	/**
	 * Make a GET request that returns an empty error string
	 *
	 * @throws Exception
	 */
	@Test
	public void noError() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertEquals("", request.body());
	}

	/**
	 * Verify 'Server' header
	 *
	 * @throws Exception
	 */
	@Test
	public void serverHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("Server", "aserver");
			}
		});
		assertEquals("aserver", get(url).server());
	}

	/**
	 * Verify 'Expires' header
	 *
	 * @throws Exception
	 */
	@Test
	public void expiresHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setDateHeader("Expires", 1234000);
			}
		});
		assertEquals(1234000, get(url).expires());
	}

	/**
	 * Verify 'Last-Modified' header
	 *
	 * @throws Exception
	 */
	@Test
	public void lastModifiedHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setDateHeader("Last-Modified", 555000);
			}
		});
		assertEquals(555000, get(url).lastModified());
	}

	/**
	 * Verify 'Date' header
	 *
	 * @throws Exception
	 */
	@Test
	public void dateHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setDateHeader("Date", 66000);
			}
		});
		assertEquals(66000, get(url).date());
	}

	/**
	 * Verify 'ETag' header
	 *
	 * @throws Exception
	 */
	@Test
	public void eTagHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("ETag", "abcd");
			}
		});
		assertEquals("abcd", get(url).eTag());
	}

	/**
	 * Verify 'Location' header
	 *
	 * @throws Exception
	 */
	@Test
	public void locationHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("Location", "http://nowhere");
			}
		});
		assertEquals("http://nowhere", get(url).location());
	}

	/**
	 * Verify 'Content-Encoding' header
	 *
	 * @throws Exception
	 */
	@Test
	public void contentEncodingHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("Content-Encoding", "gzip");
			}
		});
		assertEquals("gzip", get(url).contentEncoding());
	}

	/**
	 * Verify 'Content-Type' header
	 *
	 * @throws Exception
	 */
	@Test
	public void contentTypeHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("Content-Type", "text/html");
			}
		});
		assertEquals("text/html", get(url).contentType());
	}

	/**
	 * Verify 'Cache-Control' header
	 *
	 * @throws Exception
	 */
	@Test
	public void cacheControlHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("Cache-Control", "no-cache");
			}
		});
		assertEquals("no-cache", get(url).cacheControl());
	}

	/**
	 * Verify headers
	 *
	 * @throws Exception
	 */
	@Test
	public void headers() throws Exception {
		final AtomicReference<String> h1 = new AtomicReference<String>();
		final AtomicReference<String> h2 = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				h1.set(request.getHeader("h1"));
				h2.set(request.getHeader("h2"));
			}
		});
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("h1", "v1");
		headers.put("h2", "v2");
		assertTrue(get(url).headers(headers).ok());
		assertEquals("v1", h1.get());
		assertEquals("v2", h2.get());
	}

	/**
	 * Verify setting number header
	 *
	 * @throws Exception
	 */
	@Test
	public void numberHead() throws Exception {
		final AtomicReference<String> h1 = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				h1.set(request.getHeader("h1"));
			}
		});
		assertTrue(get(url).header("h1", 5).ok());
		assertEquals("5", h1.get());
	}

	/**
	 * Verify 'User-Agent' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void userAgentHeader() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("User-Agent"));
			}
		});
		assertTrue(get(url).userAgent("browser 1.0").ok());
		assertEquals("browser 1.0", header.get());
	}

	/**
	 * Verify 'Accept' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void acceptHeader() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("Accept"));
			}
		});
		assertTrue(get(url).accept("application/json").ok());
		assertEquals("application/json", header.get());
	}

	/**
	 * Verify 'Accept' request header when calling
	 * {@link HttpRequest#acceptJson()}
	 *
	 * @throws Exception
	 */
	@Test
	public void acceptJson() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("Accept"));
			}
		});
		assertTrue(get(url).acceptJson().ok());
		assertEquals("application/json", header.get());
	}

	/**
	 * Verify 'If-None-Match' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void ifNoneMatchHeader() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("If-None-Match"));
			}
		});
		assertTrue(get(url).ifNoneMatch("eid").ok());
		assertEquals("eid", header.get());
	}

	/**
	 * Verify 'Accept-Charset' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void acceptCharsetHeader() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("Accept-Charset"));
			}
		});
		assertTrue(get(url).acceptCharset(CHARSET_UTF8).ok());
		assertEquals(CHARSET_UTF8, header.get());
	}

	/**
	 * Verify 'Accept-Encoding' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void acceptEncodingHeader() throws Exception {
		final AtomicReference<String> header = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getHeader("Accept-Encoding"));
			}
		});
		assertTrue(get(url).acceptEncoding("compress").ok());
		assertEquals("compress", header.get());
	}

	/**
	 * Verify 'If-Modified-Since' request header
	 *
	 * @throws Exception
	 */
	@Test
	public void ifModifiedSinceHeader() throws Exception {
		final AtomicLong header = new AtomicLong();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				header.set(request.getDateHeader("If-Modified-Since"));
			}
		});
		assertTrue(get(url).ifModifiedSince(5000).ok());
		assertEquals(5000, header.get());
	}

	/**
	 * Verify multipart with file, stream, number, and string parameters
	 *
	 * @throws Exception
	 */
	@Test
	public void postMultipart() throws Exception {
		final StringBuilder body = new StringBuilder();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				char[] buffer = new char[8192];
				int read;
				try {
					while ((read = request.getReader().read(buffer)) != -1)
						body.append(buffer, 0, read);
				} catch (IOException e) {
					fail();
				}
			}
		});
		File file = File.createTempFile("body", ".txt");
		File file2 = File.createTempFile("body", ".txt");
		new FileWriter(file).append("content1").close();
		new FileWriter(file2).append("content4").close();
		HttpRequest request = post(url);
		request.part("description", "content2");
		request.part("size", file.length());
		request.part("body", file.getName(), file);
		request.part("file", file2);
		request.part("stream", new ByteArrayInputStream("content3".getBytes()));
		assertTrue(request.ok());
		assertTrue(body.toString().contains("content1\r\n"));
		assertTrue(body.toString().contains("content2\r\n"));
		assertTrue(body.toString().contains("content3\r\n"));
		assertTrue(body.toString().contains("content4\r\n"));
		assertTrue(body.toString().contains(
				Long.toString(file.length()) + "\r\n"));
	}

	/**
	 * Verify response in {@link Appendable}
	 *
	 * @throws Exception
	 */
	@Test
	public void receiveAppendable() throws Exception {
		final StringBuilder body = new StringBuilder();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		assertTrue(post(url).receive(body).ok());
		assertEquals("content", body.toString());
	}

	/**
	 * Verify response in {@link Writer}
	 *
	 * @throws Exception
	 */
	@Test
	public void receiveWriter() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		StringWriter writer = new StringWriter();
		assertTrue(post(url).receive(writer).ok());
		assertEquals("content", writer.toString());
	}

	/**
	 * Verify response via a {@link PrintStream}
	 *
	 * @throws Exception
	 */
	@Test
	public void receivePrintStream() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream stream = new PrintStream(output, true, CHARSET_UTF8);
		assertTrue(post(url).receive(stream).ok());
		stream.close();
		assertEquals("content", output.toString(CHARSET_UTF8));
	}

	/**
	 * Verify response in {@link File}
	 *
	 * @throws Exception
	 */
	@Test
	public void receiveFile() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		File output = File.createTempFile("output", ".txt");
		assertTrue(post(url).receive(output).ok());
		StringBuilder buffer = new StringBuilder();
		BufferedReader reader = new BufferedReader(new FileReader(output));
		int read;
		while ((read = reader.read()) != -1)
			buffer.append((char) read);
		reader.close();
		assertEquals("content", buffer.toString());
	}

	/**
	 * Verify certificate and host helpers on HTTPS connection
	 *
	 * @throws Exception
	 */
	@Test
	public void httpsTrust() throws Exception {
		assertNotNull(get("https://localhost").trustAllCerts().trustAllHosts());
	}

	/**
	 * Verify certificate and host helpers ignore non-HTTPS connection
	 *
	 * @throws Exception
	 */
	@Test
	public void httpTrust() throws Exception {
		assertNotNull(get("http://localhost").trustAllCerts().trustAllHosts());
	}

	/**
	 * Send a stream that throws an exception when read from
	 *
	 * @throws Exception
	 */
	@Test
	public void sendErrorReadStream() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		final IOException readCause = new IOException();
		final IOException closeCause = new IOException();
		InputStream stream = new InputStream() {

			public int read() throws IOException {
				throw readCause;
			}

			public void close() throws IOException {
				throw closeCause;
			}
		};
		try {
			post(url).send(stream);
			fail("Exception not thrown");
		} catch (HttpRequestException e) {
			assertEquals(readCause, e.getCause());
		}
	}

	/**
	 * Send a stream that throws an exception when read from
	 *
	 * @throws Exception
	 */
	@Test
	public void sendErrorCloseStream() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				try {
					response.getWriter().print("content");
				} catch (IOException e) {
					fail();
				}
			}
		});
		final IOException closeCause = new IOException();
		InputStream stream = new InputStream() {

			public int read() throws IOException {
				return -1;
			}

			public void close() throws IOException {
				throw closeCause;
			}
		};
		try {
			post(url).ignoreCloseExceptions(false).send(stream);
			fail("Exception not thrown");
		} catch (HttpRequestException e) {
			assertEquals(closeCause, e.getCause());
		}
	}

	/**
	 * Make a GET request and get the code using an {@link AtomicInteger}
	 *
	 * @throws Exception
	 */
	@Test
	public void getToOutputCode() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
			}
		});
		AtomicInteger code = new AtomicInteger(0);
		get(url).code(code);
		assertEquals(HTTP_OK, code.get());
	}

	/**
	 * Make a GET request that should be compressed
	 *
	 * @throws Exception
	 */
	@Test
	public void getGzipped() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				if (!"gzip".equals(request.getHeader("Accept-Encoding")))
					return;

				response.setHeader("Content-Encoding", "gzip");
				GZIPOutputStream output;
				try {
					output = new GZIPOutputStream(response.getOutputStream());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				try {
					output.write("hello compressed".getBytes(CHARSET_UTF8));
				} catch (IOException e) {
					throw new RuntimeException(e);
				} finally {
					try {
						output.close();
					} catch (IOException ignored) {
						// Ignored
					}
				}
			}
		});
		HttpRequest request = get(url).acceptGzipEncoding().uncompress(true);
		assertTrue(request.ok());
		assertEquals("hello compressed", request.body(CHARSET_UTF8));
	}

	/**
	 * Get header with multiple response values
	 *
	 * @throws Exception
	 */
	@Test
	public void getHeaders() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.addHeader("a", "1");
				response.addHeader("a", "2");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		String[] values = request.headers("a");
		assertNotNull(values);
		assertEquals(2, values.length);
		assertTrue(Arrays.asList(values).contains("1"));
		assertTrue(Arrays.asList(values).contains("2"));
	}

	/**
	 * Get header values when not set in response
	 *
	 * @throws Exception
	 */
	@Test
	public void getEmptyHeaders() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		String[] values = request.headers("a");
		assertNotNull(values);
		assertEquals(0, values.length);
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getSingleParameter() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=d");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertEquals("d", request.parameter("a", "c"));
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getMultipleParameters() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=d;e=f");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertEquals("d", request.parameter("a", "c"));
		assertEquals("f", request.parameter("a", "e"));
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getSingleParameterQuoted() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=\"d\"");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertEquals("d", request.parameter("a", "c"));
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getMultipleParametersQuoted() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=\"d\";e=\"f\"");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertEquals("d", request.parameter("a", "c"));
		assertEquals("f", request.parameter("a", "e"));
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getMissingParameter() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=d");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertNull(request.parameter("a", "e"));
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getParameterFromMissingHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=d");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertNull(request.parameter("b", "c"));
		assertTrue(request.parameters("b").isEmpty());
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getEmptyParameter() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;c=");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertNull(request.parameter("a", "c"));
		assertTrue(request.parameters("a").isEmpty());
	}

	/**
	 * Get header parameter value
	 *
	 * @throws Exception
	 */
	@Test
	public void getEmptyParameters() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "b;");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		assertNull(request.parameter("a", "c"));
		assertTrue(request.parameters("a").isEmpty());
	}

	/**
	 * Get header parameter values
	 *
	 * @throws Exception
	 */
	@Test
	public void getParameters() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "value;b=c;d=e");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		Map<String, String> params = request.parameters("a");
		assertNotNull(params);
		assertEquals(2, params.size());
		assertEquals("c", params.get("b"));
		assertEquals("e", params.get("d"));
	}

	/**
	 * Get header parameter values
	 *
	 * @throws Exception
	 */
	@Test
	public void getQuotedParameters() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "value;b=\"c\";d=\"e\"");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		Map<String, String> params = request.parameters("a");
		assertNotNull(params);
		assertEquals(2, params.size());
		assertEquals("c", params.get("b"));
		assertEquals("e", params.get("d"));
	}

	/**
	 * Get header parameter values
	 *
	 * @throws Exception
	 */
	@Test
	public void getMixQuotedParameters() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("a", "value; b=c; d=\"e\"");
			}
		});
		HttpRequest request = get(url);
		assertTrue(request.ok());
		Map<String, String> params = request.parameters("a");
		assertNotNull(params);
		assertEquals(2, params.size());
		assertEquals("c", params.get("b"));
		assertEquals("e", params.get("d"));
	}

	/**
	 * Verify getting date header with default value
	 *
	 * @throws Exception
	 */
	@Test
	public void missingDateHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
			}
		});
		assertEquals(1234L, get(url).dateHeader("missing", 1234L));
	}

	/**
	 * Verify getting date header with default value
	 *
	 * @throws Exception
	 */
	@Test
	public void malformedDateHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("malformed", "not a date");
			}
		});
		assertEquals(1234L, get(url).dateHeader("malformed", 1234L));
	}

	/**
	 * Verify getting int header with default value
	 *
	 * @throws Exception
	 */
	@Test
	public void missingIntHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
			}
		});
		assertEquals(4321, get(url).intHeader("missing", 4321));
	}

	/**
	 * Verify getting int header with default value
	 *
	 * @throws Exception
	 */
	@Test
	public void malformedIntHeader() throws Exception {
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				response.setStatus(HTTP_OK);
				response.setHeader("malformed", "not an integer");
			}
		});
		assertEquals(4321, get(url).intHeader("malformed", 4321));
	}

	/**
	 * Verify sending form data as a sequence of {@link Entry} objects
	 *
	 * @throws Exception
	 */
	@Test
	public void postFormAsEntries() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("name", "user");
		data.put("number", "100");
		HttpRequest request = post(url);
		for (Entry<String, String> entry : data.entrySet())
			request.form(entry);
		int code = request.code();
		assertEquals(HTTP_OK, code);
		assertEquals("name=user&number=100", body.get());
	}

	/**
	 * Verify sending form data where entry value is null
	 *
	 * @throws Exception
	 */
	@Test
	public void postFormEntryWithNullValue() throws Exception {
		final AtomicReference<String> body = new AtomicReference<String>();
		String url = setUp(new RequestHandler() {

			public void handle(Request request, HttpServletResponse response) {
				body.set(new String(read()));
				response.setStatus(HTTP_OK);
			}
		});
		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("name", null);
		HttpRequest request = post(url);
		for (Entry<String, String> entry : data.entrySet())
			request.form(entry);
		int code = request.code();
		assertEquals(HTTP_OK, code);
		assertEquals("name=", body.get());
	}
}
