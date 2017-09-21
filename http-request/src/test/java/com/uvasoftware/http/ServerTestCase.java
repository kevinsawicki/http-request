package com.uvasoftware.http;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.AfterClass;
import org.junit.Before;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.uvasoftware.http.HttpRequest.CHARSET_UTF8;

/**
 * Base test case that provides a running HTTP server
 */
public class ServerTestCase {

  static final AtomicInteger proxyHitCount = new AtomicInteger(0);
  static final AtomicReference<String> proxyUser = new AtomicReference<String>();
  static final AtomicReference<String> proxyPassword = new AtomicReference<String>();
  /**
   * Server
   */
  private static Server server;
  private static Server proxy;
  static int proxyPort;

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
    Connector connector = new SelectChannelConnector();
    connector.setPort(0);

    //    TLS connector
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePassword("password");
    sslContextFactory.setKeyStorePath("src/test/resources/keystore.jks");
    Connector connector1 = new SslSocketConnector(sslContextFactory);
    connector1.setPort(8081);

    server.setConnectors(new Connector[]{connector, connector1});
    server.start();

    proxy = new Server();
    Connector proxyConnector = new SelectChannelConnector();
    proxyConnector.setPort(0);
    proxy.setConnectors(new Connector[]{proxyConnector});

    ServletHandler proxyHandler = new ServletHandler();

    RequestHandler proxyCountingHandler = new RequestHandler() {

      @Override
      public void handle(Request request, HttpServletResponse response) {
        proxyHitCount.incrementAndGet();
        String auth = request.getHeader("Proxy-Authorization");
        auth = auth.substring(auth.indexOf(' ') + 1);
        try {
          auth = B64Code.decode(auth, CHARSET_UTF8);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
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

    ServletHolder proxyHolder = proxyHandler.addServletWithMapping("org.eclipse.jetty.servlets.ProxyServlet", "/");
    proxyHolder.setAsyncSupported(true);

    proxy.start();

    proxyPort = proxyConnector.getLocalPort();

    return "http://localhost:" + connector.getLocalPort();
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

  @Before
  public void clearProxyHitCount() {
    proxyHitCount.set(0);
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
    byte[] read() {
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
    void write(String value) {
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
}
