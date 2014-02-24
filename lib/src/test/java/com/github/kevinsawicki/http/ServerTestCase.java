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

import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.B64Code;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Before;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.kevinsawicki.http.HttpRequest.CHARSET_UTF8;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.AfterClass;

/**
 * Base test case that provides a running HTTP server
 */
public class ServerTestCase {

  static {
    System.setProperty("javax.net.ssl.keyStore","/jetty.pkcs12");
    System.setProperty("org.eclipse.jetty.ssl.password","changeit");
    System.setProperty("javax.net.ssl.keyStorePassword","changeit");
    System.setProperty("javax.net.ssl.keyStoreType","PKCS12");
  }

  /**
   * Simplified handler
   */
  protected static abstract class RequestHandler extends AbstractHandler {

    private Request request;

    private HttpServletResponse response;

    /**
     * Handle request
     *
     * @param request
     * @param response
     */
    public abstract void handle(Request request, HttpServletResponse response);

    /**
     * Read content
     *
     * @return content
     */
    protected byte[] read() {
      ByteArrayOutputStream content = new ByteArrayOutputStream();
      final byte[] buffer = new byte[8196];
      int read;
      try {
        InputStream input = request.getInputStream();
        while ((read = input.read(buffer)) != -1)
          content.write(buffer, 0, read);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return content.toByteArray();
    }

    /**
     * Write value
     *
     * @param value
     */
    protected void write(String value) {
      try {
        response.getWriter().print(value);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Write line
     *
     * @param value
     */
    protected void writeln(String value) {
      try {
        response.getWriter().println(value);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public void handle(String target, Request baseRequest,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      this.request = (Request) request;
      this.response = response;
      this.request.setHandled(true);
      handle(this.request, response);
    }

  }

  /**
   * Server
   */
  protected static Server server;
  protected static Server proxy;
  protected static int proxyPort;
  protected static final AtomicInteger proxyHitCount = new AtomicInteger(0);
  protected static final AtomicReference<String> proxyUser = new AtomicReference<String>();
  protected static final AtomicReference<String> proxyPassword = new AtomicReference<String>();

  /**
   * Set up server with handler
   *
   * @param handler
   * @return port
   * @throws Exception
   */
  public static String setUp(final Handler handler) throws Exception {
    server = new Server();
    if (handler != null)
      server.setHandler(handler);

    SslContextFactory sslContextFactory = new SslContextFactory(System.getProperty(HttpRequest.SSLConfig.KEY_STORE));

    sslContextFactory.setKeyStorePassword(System.getProperty(HttpRequest.SSLConfig.KEY_STORE_PASSWORD));
    sslContextFactory.setKeyStoreType(System.getProperty(HttpRequest.SSLConfig.KEY_STORE_TYPE));
    sslContextFactory.setNeedClientAuth(true);

    ServerConnector https = new ServerConnector(server, new SslConnectionFactory(sslContextFactory,"http/1.1"));
    https.setPort(8443);
    https.setIdleTimeout(5000);

    ServerConnector http = new ServerConnector(server,new HttpConnectionFactory());
    http.setPort(0);

    server.setConnectors(new Connector[] { http, https });
    server.start();

    proxy = new Server();
    ServerConnector proxyConnector = new ServerConnector(proxy, new HttpConnectionFactory());
    proxyConnector.setPort(0);

    proxy.setConnectors(new Connector[] { proxyConnector });

    ServletHandler proxyHandler = new ServletHandler();

    RequestHandler proxyCountingHandler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        proxyHitCount.incrementAndGet();
        String auth = request.getHeader("Proxy-Authorization");
        auth = auth.substring(auth.indexOf(' ') + 1);
        auth = B64Code.decode(auth, CHARSET_UTF8);
        int colon = auth.indexOf(':');
        proxyUser.set(auth.substring(0, colon));
        proxyPassword.set(auth.substring(colon + 1));
        request.setHandled(false);
      }
    };

    HandlerList handlerList = new HandlerList();
    handlerList.addHandler(proxyCountingHandler);
    handlerList.addHandler(proxyHandler);
    proxy.setHandler(handlerList);

    ServletHolder proxyHolder = proxyHandler.addServletWithMapping(ProxyServlet.class, "/*");
    proxyHolder.setAsyncSupported(true);
    proxyHolder.setInitParameter("maxThreads", "200");

    proxy.start();

    proxyPort = proxyConnector.getLocalPort();

    return "http://localhost:" + http.getLocalPort();
  }

  @Before
  public void clearProxyHitCount() {
    proxyHitCount.set(0);
  }

  /**
   * Tear down server if created
   *
   * @throws Exception
   */
  @AfterClass
  public static void tearDown() throws Exception {
    if (server != null)
      server.stop();
    if (proxy != null)
      proxy.stop();
  }
}
