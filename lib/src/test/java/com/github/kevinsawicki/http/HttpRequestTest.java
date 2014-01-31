/*
 * Copyright (c) 2014 Kevin Sawicki <kevinsawicki@gmail.com>
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
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;
import com.github.kevinsawicki.http.HttpRequest.ConnectionFactory;
import com.github.kevinsawicki.http.HttpRequest.UploadProgress;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.B64Code;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests of {@link HttpRequest}
 */
public class HttpRequestTest extends ServerTestCase {

  private static String url;

  private static RequestHandler handler;

  /**
   * Set up server
   *
   * @throws Exception
   */
  @BeforeClass
  public static void startServer() throws Exception {
    url = setUp(new RequestHandler() {

      @Override
      public void handle(String target, Request baseRequest,
          HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
        if (handler != null)
          handler.handle(target, baseRequest, request, response);
      }

      @Override
      public void handle(Request request, HttpServletResponse response) {
        if (handler != null)
          handler.handle(request, response);
      }
    });
  }

  /**
   * Clear handler
   */
  @After
  public void clearHandler() {
    handler = null;
  }

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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(url);
    assertNotNull(request.getConnection());
    assertEquals(30000, request.readTimeout(30000).getConnection()
        .getReadTimeout());
    assertEquals(50000, request.connectTimeout(50000).getConnection()
        .getConnectTimeout());
    assertEquals(2500, request.bufferSize(2500).bufferSize());
    assertFalse(request.ignoreCloseExceptions(false).ignoreCloseExceptions());
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
    assertTrue(request.isBodyEmpty());
    assertEquals(request.url().toString(), url);
    assertEquals("GET", request.method());
  }

  /**
   * Make a GET request with an empty body response
   *
   * @throws Exception
   */
  @Test
  public void getUrlEmpty() throws Exception {
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(new URL(url));
    assertNotNull(request.getConnection());
    int code = request.code();
    assertTrue(request.ok());
    assertFalse(request.created());
    assertFalse(request.noContent());
    assertFalse(request.badRequest());
    assertFalse(request.serverError());
    assertFalse(request.notFound());
    assertEquals("GET", method.get());
    assertEquals("OK", request.message());
    assertEquals(HTTP_OK, code);
    assertEquals("", request.body());
  }

  /**
   * Make a GET request with an empty body response
   *
   * @throws Exception
   */
  @Test
  public void getNoContent() throws Exception {
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_NO_CONTENT);
      }
    };
    HttpRequest request = get(new URL(url));
    assertNotNull(request.getConnection());
    int code = request.code();
    assertFalse(request.ok());
    assertFalse(request.created());
    assertTrue(request.noContent());
    assertFalse(request.badRequest());
    assertFalse(request.serverError());
    assertFalse(request.notFound());
    assertEquals("GET", method.get());
    assertEquals("No Content", request.message());
    assertEquals(HTTP_NO_CONTENT, code);
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        path.set(request.getPathInfo());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        path.set(request.getPathInfo());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        path.set(request.getPathInfo());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = delete(url);
    assertNotNull(request.getConnection());
    assertTrue(request.ok());
    assertFalse(request.notFound());
    assertEquals("DELETE", method.get());
    assertEquals("", request.body());
    assertEquals("DELETE", request.method());
  }

  /**
   * Make a DELETE request with an empty body response
   *
   * @throws Exception
   */
  @Test
  public void deleteUrlEmpty() throws Exception {
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_CREATED);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        response.setStatus(HTTP_CREATED);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };

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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        length.set(request.getContentLength());
        response.setStatus(HTTP_OK);
      }
    };
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
    final AtomicReference<String> contentType = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        contentType.set(request.getContentType());
        response.setStatus(HTTP_OK);
      }
    };
    Map<String, String> data = new LinkedHashMap<String, String>();
    data.put("name", "user");
    data.put("number", "100");
    int code = post(url).form(data).form("zip", "12345").code();
    assertEquals(HTTP_OK, code);
    assertEquals("name=user&number=100&zip=12345", body.get());
    assertEquals("application/x-www-form-urlencoded; charset=UTF-8",
        contentType.get());
  }

  /**
   * Make a post of form data
   *
   * @throws Exception
   */
  @Test
  public void postFormWithNoCharset() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    final AtomicReference<String> contentType = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        contentType.set(request.getContentType());
        response.setStatus(HTTP_OK);
      }
    };
    Map<String, String> data = new LinkedHashMap<String, String>();
    data.put("name", "user");
    data.put("number", "100");
    int code = post(url).form(data, null).form("zip", "12345").code();
    assertEquals(HTTP_OK, code);
    assertEquals("name=user&number=100&zip=12345", body.get());
    assertEquals("application/x-www-form-urlencoded", contentType.get());
  }

  /**
   * Make a post with an empty form data map
   *
   * @throws Exception
   */
  @Test
  public void postEmptyForm() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
        encoding.set(request.getHeader("Transfer-Encoding"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        write("hello");
      }
    };
    HttpRequest request = get(url);
    assertEquals(HTTP_OK, request.code());
    assertEquals("hello", request.body());
    assertEquals("hello".getBytes().length, request.contentLength());
    assertFalse(request.isBodyEmpty());
  }

  /**
   * Make a GET request with a response that includes a charset parameter
   *
   * @throws Exception
   */
  @Test
  public void getWithResponseCharset() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setContentType("text/html; charset=UTF-8");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setContentType("text/html; param1=val1; charset=UTF-8");
      }
    };
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
    handler = new RequestHandler() {

      @Override
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
    };
    assertTrue(get(url).basic("user", "p4ssw0rd").ok());
    assertEquals("user", user.get());
    assertEquals("p4ssw0rd", password.get());
  }

  /**
   * Make a GET request with basic proxy authentication specified
   *
   * @throws Exception
   */
  @Test
  public void basicProxyAuthentication() throws Exception {
    final AtomicBoolean finalHostReached = new AtomicBoolean(false);
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        finalHostReached.set(true);
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(get(url).useProxy("localhost", proxyPort).proxyBasic("user", "p4ssw0rd").ok());
    assertEquals("user", proxyUser.get());
    assertEquals("p4ssw0rd", proxyPassword.get());
    assertEquals(true, finalHostReached.get());
    assertEquals(1, proxyHitCount.get());
  }

  /**
   * Make a GET and get response as a input stream reader
   *
   * @throws Exception
   */
  @Test
  public void getReader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        write("hello");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };

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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        write("hello");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        write("hello");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        write("hello");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        write("error");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("Server", "aserver");
      }
    };
    assertEquals("aserver", get(url).server());
  }

  /**
   * Verify 'Expires' header
   *
   * @throws Exception
   */
  @Test
  public void expiresHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setDateHeader("Expires", 1234000);
      }
    };
    assertEquals(1234000, get(url).expires());
  }

  /**
   * Verify 'Last-Modified' header
   *
   * @throws Exception
   */
  @Test
  public void lastModifiedHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setDateHeader("Last-Modified", 555000);
      }
    };
    assertEquals(555000, get(url).lastModified());
  }

  /**
   * Verify 'Date' header
   *
   * @throws Exception
   */
  @Test
  public void dateHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setDateHeader("Date", 66000);
      }
    };
    assertEquals(66000, get(url).date());
  }

  /**
   * Verify 'ETag' header
   *
   * @throws Exception
   */
  @Test
  public void eTagHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("ETag", "abcd");
      }
    };
    assertEquals("abcd", get(url).eTag());
  }

  /**
   * Verify 'Location' header
   *
   * @throws Exception
   */
  @Test
  public void locationHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("Location", "http://nowhere");
      }
    };
    assertEquals("http://nowhere", get(url).location());
  }

  /**
   * Verify 'Content-Encoding' header
   *
   * @throws Exception
   */
  @Test
  public void contentEncodingHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("Content-Encoding", "gzip");
      }
    };
    assertEquals("gzip", get(url).contentEncoding());
  }

  /**
   * Verify 'Content-Type' header
   *
   * @throws Exception
   */
  @Test
  public void contentTypeHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("Content-Type", "text/html");
      }
    };
    assertEquals("text/html", get(url).contentType());
  }

  /**
   * Verify 'Content-Type' header
   *
   * @throws Exception
   */
  @Test
  public void requestContentType() throws Exception {
    final AtomicReference<String> contentType = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        contentType.set(request.getContentType());
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(post(url).contentType("text/html", "UTF-8").ok());
    assertEquals("text/html; charset=UTF-8", contentType.get());
  }

  /**
   * Verify 'Content-Type' header
   *
   * @throws Exception
   */
  @Test
  public void requestContentTypeNullCharset() throws Exception {
    final AtomicReference<String> contentType = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        contentType.set(request.getContentType());
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(post(url).contentType("text/html", null).ok());
    assertEquals("text/html", contentType.get());
  }

  /**
   * Verify 'Content-Type' header
   *
   * @throws Exception
   */
  @Test
  public void requestContentTypeEmptyCharset() throws Exception {
    final AtomicReference<String> contentType = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        contentType.set(request.getContentType());
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(post(url).contentType("text/html", "").ok());
    assertEquals("text/html", contentType.get());
  }

  /**
   * Verify 'Cache-Control' header
   *
   * @throws Exception
   */
  @Test
  public void cacheControlHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("Cache-Control", "no-cache");
      }
    };
    assertEquals("no-cache", get(url).cacheControl());
  }

  /**
   * Verify setting headers
   *
   * @throws Exception
   */
  @Test
  public void headers() throws Exception {
    final AtomicReference<String> h1 = new AtomicReference<String>();
    final AtomicReference<String> h2 = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        h1.set(request.getHeader("h1"));
        h2.set(request.getHeader("h2"));
      }
    };
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("h1", "v1");
    headers.put("h2", "v2");
    assertTrue(get(url).headers(headers).ok());
    assertEquals("v1", h1.get());
    assertEquals("v2", h2.get());
  }

  /**
   * Verify setting headers
   *
   * @throws Exception
   */
  @Test
  public void emptyHeaders() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(get(url).headers(Collections.<String, String> emptyMap()).ok());
  }

  /**
   * Verify getting all headers
   *
   * @throws Exception
   */
  @Test
  public void getAllHeaders() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "a");
        response.setHeader("b", "b");
        response.addHeader("a", "another");
      }
    };
    Map<String, List<String>> headers = get(url).headers();
    assertEquals(headers.size(), 5);
    assertEquals(headers.get("a").size(), 2);
    assertTrue(headers.get("b").get(0).equals("b"));
  }

  /**
   * Verify setting number header
   *
   * @throws Exception
   */
  @Test
  public void numberHeader() throws Exception {
    final AtomicReference<String> h1 = new AtomicReference<String>();
    final AtomicReference<String> h2 = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        h1.set(request.getHeader("h1"));
        h2.set(request.getHeader("h2"));
      }
    };
    assertTrue(get(url).header("h1", 5).header("h2", (Number) null).ok());
    assertEquals("5", h1.get());
    assertEquals("", h2.get());
  }

  /**
   * Verify 'User-Agent' request header
   *
   * @throws Exception
   */
  @Test
  public void userAgentHeader() throws Exception {
    final AtomicReference<String> header = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("User-Agent"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("Accept"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("Accept"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("If-None-Match"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("Accept-Charset"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getHeader("Accept-Encoding"));
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        header.set(request.getDateHeader("If-Modified-Since"));
      }
    };
    assertTrue(get(url).ifModifiedSince(5000).ok());
    assertEquals(5000, header.get());
  }

  /**
   * Verify 'Referer' header
   *
   * @throws Exception
   */
  @Test
  public void refererHeader() throws Exception {
    final AtomicReference<String> referer = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        referer.set(request.getHeader("Referer"));
        response.setStatus(HTTP_OK);
      }
    };
    assertTrue(post(url).referer("http://heroku.com").ok());
    assertEquals("http://heroku.com", referer.get());
  }

  /**
   * Verify multipart with file, stream, number, and string parameters
   *
   * @throws Exception
   */
  @Test
  public void postMultipart() throws Exception {
    final StringBuilder body = new StringBuilder();
    handler = new RequestHandler() {

      @Override
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
    };
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
    assertTrue(body.toString().contains(Long.toString(file.length()) + "\r\n"));
  }

  /**
   * Verify multipart with content type part header
   *
   * @throws Exception
   */
  @Test
  public void postMultipartWithContentType() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        body.set(new String(read()));
      }
    };
    HttpRequest request = post(url);
    request.part("body", null, "application/json", "contents");
    assertTrue(request.ok());
    assertTrue(body.toString().contains("Content-Type: application/json"));
    assertTrue(body.toString().contains("contents\r\n"));
  }

  /**
   * Verify response in {@link Appendable}
   *
   * @throws Exception
   */
  @Test
  public void receiveAppendable() throws Exception {
    final StringBuilder body = new StringBuilder();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
   * Verify hostname verifier is set and accepts all
   */
  @Test
  public void verifierAccepts() {
    HttpRequest request = get("https://localhost");
    HttpsURLConnection connection = (HttpsURLConnection) request
        .getConnection();
    request.trustAllHosts();
    assertNotNull(connection.getHostnameVerifier());
    assertTrue(connection.getHostnameVerifier().verify(null, null));
  }

  /**
   * Verify single hostname verifier is created across all calls
   */
  @Test
  public void singleVerifier() {
    HttpRequest request1 = get("https://localhost").trustAllHosts();
    HttpRequest request2 = get("https://localhost").trustAllHosts();
    assertNotNull(((HttpsURLConnection) request1.getConnection())
        .getHostnameVerifier());
    assertNotNull(((HttpsURLConnection) request2.getConnection())
        .getHostnameVerifier());
    assertEquals(
        ((HttpsURLConnection) request1.getConnection()).getHostnameVerifier(),
        ((HttpsURLConnection) request2.getConnection()).getHostnameVerifier());
  }

  /**
   * Verify single SSL socket factory is created across all calls
   */
  @Test
  public void singleSslSocketFactory() {
    HttpRequest request1 = get("https://localhost").trustAllCerts();
    HttpRequest request2 = get("https://localhost").trustAllCerts();
    assertNotNull(((HttpsURLConnection) request1.getConnection())
        .getSSLSocketFactory());
    assertNotNull(((HttpsURLConnection) request2.getConnection())
        .getSSLSocketFactory());
    assertEquals(
        ((HttpsURLConnection) request1.getConnection()).getSSLSocketFactory(),
        ((HttpsURLConnection) request2.getConnection()).getSSLSocketFactory());
  }

  /**
   * Send a stream that throws an exception when read from
   *
   * @throws Exception
   */
  @Test
  public void sendErrorReadStream() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("content");
        } catch (IOException e) {
          fail();
        }
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
    AtomicInteger code = new AtomicInteger(0);
    get(url).code(code);
    assertEquals(HTTP_OK, code.get());
  }

  /**
   * Make a GET request and get the body using an {@link AtomicReference}
   *
   * @throws Exception
   */
  @Test
  public void getToOutputBody() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("hello world");
        } catch (IOException e) {
          fail();
        }
      }
    };
    AtomicReference<String> body = new AtomicReference<String>(null);
    get(url).body(body);
    assertEquals("hello world", body.get());
  }

  /**
   * Make a GET request and get the body using an {@link AtomicReference}
   *
   * @throws Exception
   */
  @Test
  public void getToOutputBodyWithCharset() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        try {
          response.getWriter().print("hello world");
        } catch (IOException e) {
          fail();
        }
      }
    };
    AtomicReference<String> body = new AtomicReference<String>(null);
    get(url).body(body, CHARSET_UTF8);
    assertEquals("hello world", body.get());
  }


  /**
   * Make a GET request that should be compressed
   *
   * @throws Exception
   */
  @Test
  public void getGzipped() throws Exception {
    handler = new RequestHandler() {

      @Override
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
    };
    HttpRequest request = get(url).acceptGzipEncoding().uncompress(true);
    assertTrue(request.ok());
    assertEquals("hello compressed", request.body(CHARSET_UTF8));
  }

  /**
   * Make a GET request that should be compressed but isn't
   *
   * @throws Exception
   */
  @Test
  public void getNonGzippedWithUncompressEnabled() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        if (!"gzip".equals(request.getHeader("Accept-Encoding")))
          return;

        write("hello not compressed");
      }
    };
    HttpRequest request = get(url).acceptGzipEncoding().uncompress(true);
    assertTrue(request.ok());
    assertEquals("hello not compressed", request.body(CHARSET_UTF8));
  }

  /**
   * Get header with multiple response values
   *
   * @throws Exception
   */
  @Test
  public void getHeaders() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.addHeader("a", "1");
        response.addHeader("a", "2");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=d");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=d;e=f");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=\"d\"");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=\"d\";e=\"f\"");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=d");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=d");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;c=");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "b;");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "value;b=c;d=e");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "value;b=\"c\";d=\"e\"");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("a", "value; b=c; d=\"e\"");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
    assertEquals(1234L, get(url).dateHeader("missing", 1234L));
  }

  /**
   * Verify getting date header with default value
   *
   * @throws Exception
   */
  @Test
  public void malformedDateHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("malformed", "not a date");
      }
    };
    assertEquals(1234L, get(url).dateHeader("malformed", 1234L));
  }

  /**
   * Verify getting int header with default value
   *
   * @throws Exception
   */
  @Test
  public void missingIntHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };
    assertEquals(4321, get(url).intHeader("missing", 4321));
  }

  /**
   * Verify getting int header with default value
   *
   * @throws Exception
   */
  @Test
  public void malformedIntHeader() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
        response.setHeader("malformed", "not an integer");
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
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
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
    Map<String, String> data = new LinkedHashMap<String, String>();
    data.put("name", null);
    HttpRequest request = post(url);
    for (Entry<String, String> entry : data.entrySet())
      request.form(entry);
    int code = request.code();
    assertEquals(HTTP_OK, code);
    assertEquals("name=", body.get());
  }

  /**
   * Verify POST with query parameters
   *
   * @throws Exception
   */
  @Test
  public void postWithMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "user");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = post(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("POST", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify POST with query parameters
   *
   * @throws Exception
   */
  @Test
  public void postWithVaragsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = post(url, false, "name", "user", "number", "100");
    assertTrue(request.ok());
    assertEquals("POST", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify POST with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void postWithEscapedMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "us er");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = post(url, inputParams, true);
    assertTrue(request.ok());
    assertEquals("POST", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify POST with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void postWithEscapedVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = post(url, true, "name", "us er", "number", "100");
    assertTrue(request.ok());
    assertEquals("POST", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify POST with numeric query parameters
   *
   * @throws Exception
   */
  @Test
  public void postWithNumericQueryParams() throws Exception {
    Map<Object, Object> inputParams = new HashMap<Object, Object>();
    inputParams.put(1, 2);
    inputParams.put(3, 4);
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("1", request.getParameter("1"));
        outputParams.put("3", request.getParameter("3"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = post(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("POST", method.get());
    assertEquals("2", outputParams.get("1"));
    assertEquals("4", outputParams.get("3"));
  }

  /**
   * Verify GET with query parameters
   *
   * @throws Exception
   */
  @Test
  public void getWithMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "user");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("GET", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify GET with query parameters
   *
   * @throws Exception
   */
  @Test
  public void getWithVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(url, false, "name", "user", "number", "100");
    assertTrue(request.ok());
    assertEquals("GET", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify GET with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void getWithEscapedMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "us er");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(url, inputParams, true);
    assertTrue(request.ok());
    assertEquals("GET", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify GET with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void getWithEscapedVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = get(url, true, "name", "us er", "number", "100");
    assertTrue(request.ok());
    assertEquals("GET", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify DELETE with query parameters
   *
   * @throws Exception
   */
  @Test
  public void deleteWithMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "user");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = delete(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("DELETE", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify DELETE with query parameters
   *
   * @throws Exception
   */
  @Test
  public void deleteWithVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = delete(url, false, "name", "user", "number", "100");
    assertTrue(request.ok());
    assertEquals("DELETE", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify DELETE with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void deleteWithEscapedMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "us er");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = delete(url, inputParams, true);
    assertTrue(request.ok());
    assertEquals("DELETE", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify DELETE with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void deleteWithEscapedVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = delete(url, true, "name", "us er", "number", "100");
    assertTrue(request.ok());
    assertEquals("DELETE", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify PUT with query parameters
   *
   * @throws Exception
   */
  @Test
  public void putWithMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "user");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = put(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("PUT", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify PUT with query parameters
   *
   * @throws Exception
   */
  @Test
  public void putWithVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = put(url, false, "name", "user", "number", "100");
    assertTrue(request.ok());
    assertEquals("PUT", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify PUT with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void putWithEscapedMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "us er");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = put(url, inputParams, true);
    assertTrue(request.ok());
    assertEquals("PUT", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify PUT with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void putWithEscapedVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = put(url, true, "name", "us er", "number", "100");
    assertTrue(request.ok());
    assertEquals("PUT", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify HEAD with query parameters
   *
   * @throws Exception
   */
  @Test
  public void headWithMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "user");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = head(url, inputParams, false);
    assertTrue(request.ok());
    assertEquals("HEAD", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify HEAD with query parameters
   *
   * @throws Exception
   */
  @Test
  public void headWithVaragsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = head(url, false, "name", "user", "number", "100");
    assertTrue(request.ok());
    assertEquals("HEAD", method.get());
    assertEquals("user", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify HEAD with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void headWithEscapedMappedQueryParams() throws Exception {
    Map<String, String> inputParams = new HashMap<String, String>();
    inputParams.put("name", "us er");
    inputParams.put("number", "100");
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = head(url, inputParams, true);
    assertTrue(request.ok());
    assertEquals("HEAD", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Verify HEAD with escaped query parameters
   *
   * @throws Exception
   */
  @Test
  public void headWithEscapedVarargsQueryParams() throws Exception {
    final Map<String, String> outputParams = new HashMap<String, String>();
    final AtomicReference<String> method = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        method.set(request.getMethod());
        outputParams.put("name", request.getParameter("name"));
        outputParams.put("number", request.getParameter("number"));
        response.setStatus(HTTP_OK);
      }
    };
    HttpRequest request = head(url, true, "name", "us er", "number", "100");
    assertTrue(request.ok());
    assertEquals("HEAD", method.get());
    assertEquals("us er", outputParams.get("name"));
    assertEquals("100", outputParams.get("number"));
  }

  /**
   * Append with base URL with no path
   *
   * @throws Exception
   */
  @Test
  public void appendMappedQueryParamsWithNoPath() throws Exception {
    assertEquals(
        "http://test.com/?a=b",
        HttpRequest.append("http://test.com",
            Collections.singletonMap("a", "b")));
  }

  /**
   * Append with base URL with no path
   *
   * @throws Exception
   */
  @Test
  public void appendVarargsQueryParmasWithNoPath() throws Exception {
    assertEquals("http://test.com/?a=b",
        HttpRequest.append("http://test.com", "a", "b"));
  }

  /**
   * Append with base URL with path
   *
   * @throws Exception
   */
  @Test
  public void appendMappedQueryParamsWithPath() throws Exception {
    assertEquals(
        "http://test.com/segment1?a=b",
        HttpRequest.append("http://test.com/segment1",
            Collections.singletonMap("a", "b")));
    assertEquals(
        "http://test.com/?a=b",
        HttpRequest.append("http://test.com/",
            Collections.singletonMap("a", "b")));
  }

  /**
   * Append with base URL with path
   *
   * @throws Exception
   */
  @Test
  public void appendVarargsQueryParamsWithPath() throws Exception {
    assertEquals("http://test.com/segment1?a=b",
        HttpRequest.append("http://test.com/segment1", "a", "b"));
    assertEquals("http://test.com/?a=b",
        HttpRequest.append("http://test.com/", "a", "b"));
  }

  /**
   * Append multiple params
   *
   * @throws Exception
   */
  @Test
  public void appendMultipleMappedQueryParams() throws Exception {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("a", "b");
    params.put("c", "d");
    assertEquals("http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1", params));
  }

  /**
   * Append multiple params
   *
   * @throws Exception
   */
  @Test
  public void appendMultipleVarargsQueryParams() throws Exception {
    assertEquals("http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1", "a", "b", "c", "d"));
  }

  /**
   * Append null params
   *
   * @throws Exception
   */
  @Test
  public void appendNullMappedQueryParams() throws Exception {
    assertEquals("http://test.com/1",
        HttpRequest.append("http://test.com/1", (Map<?, ?>) null));
  }

  /**
   * Append null params
   *
   * @throws Exception
   */
  @Test
  public void appendNullVaragsQueryParams() throws Exception {
    assertEquals("http://test.com/1",
        HttpRequest.append("http://test.com/1", (Object[]) null));
  }

  /**
   * Append empty params
   *
   * @throws Exception
   */
  @Test
  public void appendEmptyMappedQueryParams() throws Exception {
    assertEquals(
        "http://test.com/1",
        HttpRequest.append("http://test.com/1",
            Collections.<String, String> emptyMap()));
  }

  /**
   * Append empty params
   *
   * @throws Exception
   */
  @Test
  public void appendEmptyVarargsQueryParams() throws Exception {
    assertEquals("http://test.com/1",
        HttpRequest.append("http://test.com/1", new Object[0]));
  }

  /**
   * Append params with null values
   *
   * @throws Exception
   */
  @Test
  public void appendWithNullMappedQueryParamValues() throws Exception {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("a", null);
    params.put("b", null);
    assertEquals("http://test.com/1?a=&b=",
        HttpRequest.append("http://test.com/1", params));
  }

  /**
   * Append params with null values
   *
   * @throws Exception
   */
  @Test
  public void appendWithNullVaragsQueryParamValues() throws Exception {
    assertEquals("http://test.com/1?a=&b=",
        HttpRequest.append("http://test.com/1", "a", null, "b", null));
  }

  /**
   * Try to append with wrong number of arguments
   */
  @Test(expected = IllegalArgumentException.class)
  public void appendOddNumberOfParams() {
    HttpRequest.append("http://test.com", "1");
  }

  /**
   * Append with base URL already containing a '?'
   */
  @Test
  public void appendMappedQueryParamsWithExistingQueryStart() {
    assertEquals(
        "http://test.com/1?a=b",
        HttpRequest.append("http://test.com/1?",
            Collections.singletonMap("a", "b")));
  }

  /**
   * Append with base URL already containing a '?'
   */
  @Test
  public void appendVarargsQueryParamsWithExistingQueryStart() {
    assertEquals("http://test.com/1?a=b",
        HttpRequest.append("http://test.com/1?", "a", "b"));
  }

  /**
   * Append with base URL already containing a '?'
   */
  @Test
  public void appendMappedQueryParamsWithExistingParams() {
    assertEquals(
        "http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1?a=b",
            Collections.singletonMap("c", "d")));
    assertEquals(
        "http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1?a=b&",
            Collections.singletonMap("c", "d")));

  }

  /**
   * Append with base URL already containing a '?'
   */
  @Test
  public void appendWithVarargsQueryParamsWithExistingParams() {
    assertEquals("http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1?a=b", "c", "d"));
    assertEquals("http://test.com/1?a=b&c=d",
        HttpRequest.append("http://test.com/1?a=b&", "c", "d"));
  }

  /**
   * Get a 500
   *
   * @throws Exception
   */
  @Test
  public void serverErrorCode() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_INTERNAL_ERROR);
      }
    };
    HttpRequest request = get(url);
    assertNotNull(request);
    assertTrue(request.serverError());
  }

  /**
   * Get a 400
   *
   * @throws Exception
   */
  @Test
  public void badRequestCode() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_BAD_REQUEST);
      }
    };
    HttpRequest request = get(url);
    assertNotNull(request);
    assertTrue(request.badRequest());
  }

  /**
   * Get a 304
   *
   * @throws Exception
   */
  @Test
  public void notModifiedCode() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_NOT_MODIFIED);
      }
    };
    HttpRequest request = get(url);
    assertNotNull(request);
    assertTrue(request.notModified());
  }

  /**
   * Verify data is sent when receiving response without first calling
   * {@link HttpRequest#code()}
   *
   * @throws Exception
   */
  @Test
  public void sendReceiveWithoutCode() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        try {
          response.getWriter().write("world");
        } catch (IOException ignored) {
          // Ignored
        }
        response.setStatus(HTTP_OK);
      }
    };

    HttpRequest request = post(url).ignoreCloseExceptions(false);
    assertEquals("world", request.send("hello").body());
    assertEquals("hello", body.get());
  }

  /**
   * Verify data is send when receiving response headers without first calling
   * {@link HttpRequest#code()}
   *
   * @throws Exception
   */
  @Test
  public void sendHeadersWithoutCode() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setHeader("h1", "v1");
        response.setHeader("h2", "v2");
        response.setStatus(HTTP_OK);
      }
    };

    HttpRequest request = post(url).ignoreCloseExceptions(false);
    Map<String, List<String>> headers = request.send("hello").headers();
    assertEquals("v1", headers.get("h1").get(0));
    assertEquals("v2", headers.get("h2").get(0));
    assertEquals("hello", body.get());
  }

  /**
   * Verify data is send when receiving response date header without first
   * calling {@link HttpRequest#code()}
   *
   * @throws Exception
   */
  @Test
  public void sendDateHeaderWithoutCode() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setDateHeader("Date", 1000);
        response.setStatus(HTTP_OK);
      }
    };

    HttpRequest request = post(url).ignoreCloseExceptions(false);
    assertEquals(1000, request.send("hello").date());
    assertEquals("hello", body.get());
  }

  /**
   * Verify data is send when receiving response integer header without first
   * calling {@link HttpRequest#code()}
   *
   * @throws Exception
   */
  @Test
  public void sendIntHeaderWithoutCode() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setIntHeader("Width", 9876);
        response.setStatus(HTTP_OK);
      }
    };

    HttpRequest request = post(url).ignoreCloseExceptions(false);
    assertEquals(9876, request.send("hello").intHeader("Width"));
    assertEquals("hello", body.get());
  }

  /**
   * Verify custom connection factory
   */
  @Test
  public void customConnectionFactory() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };

    ConnectionFactory factory = new ConnectionFactory() {

      public HttpURLConnection create(URL otherUrl) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
      }

      public HttpURLConnection create(URL url, Proxy proxy) throws IOException {
        throw new IOException();
      }
    };

    HttpRequest.setConnectionFactory(factory);
    int code = get("http://not/a/real/url").code();
    assertEquals(200, code);
  }

  /**
   * Verify setting a null connection factory restores to the default one
   */
  @Test
  public void nullConnectionFactory() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_OK);
      }
    };

    HttpRequest.setConnectionFactory(null);
    int code = get(url).code();
    assertEquals(200, code);
  }

  /**
   * Verify reading response body for empty 200
   *
   * @throws Exception
   */
  @Test
  public void streamOfEmptyOkResponse() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(200);
      }
    };
    assertEquals("", get(url).body());
  }

  /**
   * Verify reading response body for empty 400
   *
   * @throws Exception
   */
  @Test
  public void bodyOfEmptyErrorResponse() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_BAD_REQUEST);
      }
    };
    assertEquals("", get(url).body());
  }

  /**
   * Verify reading response body for non-empty 400
   *
   * @throws Exception
   */
  @Test
  public void bodyOfNonEmptyErrorResponse() throws Exception {
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(HTTP_BAD_REQUEST);
        try {
          response.getWriter().write("error");
        } catch (IOException ignored) {
          // Ignored
        }
      }
    };
    assertEquals("error", get(url).body());
  }

  /**
   * Verify progress callback when sending a file
   *
   * @throws Exception
   */
  @Test
  public void uploadProgressSend() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
    final File file = File.createTempFile("post", ".txt");
    new FileWriter(file).append("hello").close();

    final AtomicLong tx = new AtomicLong(0);
    UploadProgress progress = new UploadProgress() {
      public void onUpload(long transferred, long total) {
        assertEquals(file.length(), total);
        assertEquals(tx.incrementAndGet(), transferred);
      }
    };
    post(url).bufferSize(1).progress(progress).send(file).code();
    assertEquals(file.length(), tx.get());
  }

  /**
   * Verify progress callback when sending from an InputStream
   *
   * @throws Exception
   */
  @Test
  public void uploadProgressSendInputStream() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
    File file = File.createTempFile("post", ".txt");
    new FileWriter(file).append("hello").close();
    InputStream input = new FileInputStream(file);
    final AtomicLong tx = new AtomicLong(0);
    UploadProgress progress = new UploadProgress() {
      public void onUpload(long transferred, long total) {
        assertEquals(-1, total);
        assertEquals(tx.incrementAndGet(), transferred);
      }
    };
    post(url).bufferSize(1).progress(progress).send(input).code();
    assertEquals(file.length(), tx.get());
  }

  /**
   * Verify progress callback when sending from a byte array
   *
   * @throws Exception
   */
  @Test
  public void uploadProgressSendByteArray() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };

    final byte[] bytes = "hello".getBytes(CHARSET_UTF8);
    final AtomicLong tx = new AtomicLong(0);
    UploadProgress progress = new UploadProgress() {
      public void onUpload(long transferred, long total) {
        assertEquals(bytes.length, total);
        assertEquals(tx.incrementAndGet(), transferred);
      }
    };
    post(url).bufferSize(1).progress(progress).send(bytes).code();
    assertEquals(bytes.length, tx.get());
  }

  /**
   * Verify progress callback when sending from a Reader
   *
   * @throws Exception
   */
  @Test
  public void uploadProgressSendReader() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };

    final AtomicLong tx = new AtomicLong(0);
    UploadProgress progress = new UploadProgress() {
      public void onUpload(long transferred, long total) {
        assertEquals(-1, total);
        assertEquals(tx.incrementAndGet(), transferred);
      }
    };
    File file = File.createTempFile("post", ".txt");
    new FileWriter(file).append("hello").close();
    post(url).progress(progress).bufferSize(1).send(new FileReader(file)).code();
    assertEquals(file.length(), tx.get());
  }

  /**
   * Verify progress callback doesn't cause an exception when it's null
   *
   * @throws Exception
   */
  @Test
  public void nullUploadProgress() throws Exception {
    final AtomicReference<String> body = new AtomicReference<String>();
    handler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        body.set(new String(read()));
        response.setStatus(HTTP_OK);
      }
    };
    File file = File.createTempFile("post", ".txt");
    new FileWriter(file).append("hello").close();
    int code = post(url).progress(null).send(file).code();
    assertEquals(HTTP_OK, code);
    assertEquals("hello", body.get());
  }
}
