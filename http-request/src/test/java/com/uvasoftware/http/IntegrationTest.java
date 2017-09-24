package com.uvasoftware.http;

import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static com.uvasoftware.http.HttpRequest.CHARSET_UTF8;

/**
 * Base test case that provides a running HTTP server
 */
public class IntegrationTest {

  static final AtomicInteger proxyHitCount = new AtomicInteger(0);
  static final AtomicReference<String> proxyUser = new AtomicReference<String>();
  static final AtomicReference<String> proxyPassword = new AtomicReference<String>();
  private static final Logger LOG = Logger.getLogger(IntegrationTest.class.getName());
  static RequestHandler handler;
  static int proxyPort;
  static String baseUrl;
  static String baseTlsUrl;
  /**
   * Server
   */
  private static Server server;
  private static Server proxyServer;

  static {
    TestUtils.setupLogging();
  }

  @BeforeClass
  public static void startServer() throws Exception {
    setUp(new RequestHandler() {

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

  public static void setUp(final Handler handler) throws Exception {
    LOG.info("starting server");
    server = new Server();
    if (handler != null)
      server.setHandler(handler);
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);

    //    TLS connector
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePassword("password");
    sslContextFactory.setKeyStorePath("src/test/resources/keystore.jks");
    ServerConnector connector1 = new ServerConnector(server, sslContextFactory);
    connector1.setPort(0);

    server.setConnectors(new Connector[]{connector, connector1});
    server.start();

    proxyServer = new Server();
    ServerConnector connector2 = new ServerConnector(proxyServer);
    connector2.setPort(0);
    proxyServer.setConnectors(new Connector[]{connector2});

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
    proxyServer.setHandler(handlerList);

    ServletHolder proxyHolder = proxyHandler.addServletWithMapping(AsyncProxyServlet.class, "/");
    proxyHolder.setInitParameter("maxThreads", "10");
    proxyHolder.setAsyncSupported(true);

    proxyServer.start();

    proxyPort = connector2.getLocalPort();

    baseUrl = "http://localhost:" + connector.getLocalPort();
    baseTlsUrl = "https://localhost:" + connector1.getLocalPort();
  }

  /**
   * Tear down server if created
   */
  @AfterClass
  public static void tearDown() throws Exception {
    LOG.info("SHUTTING DOWN...");
    if (server != null) {
      server.stop();
    }
    if (proxyServer != null) {
      proxyServer.stop();
    }
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

    public abstract void handle(Request request, HttpServletResponse response);

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

    void write(String value) {
      try {
        response.getWriter().print(value);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

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
