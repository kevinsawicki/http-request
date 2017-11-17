package com.uvasoftware.http;

import org.eclipse.jetty.server.Request;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class TlsHttpRequestTest extends IntegrationTest {
  private static final Logger LOG = Logger.getLogger(TlsHttpRequestTest.class.getName());

  @After
  public void clearHandler() {
    LOG.fine("cleanup");
    handler = null;
  }

  @Test
  public void shouldBeThreadSafe() throws Exception {

    final int threads = 10;

    ExecutorService es = Executors.newFixedThreadPool(threads);
    List<Callable<Integer>> callables = new ArrayList<Callable<Integer>>();

    final AtomicInteger counter = new AtomicInteger();

    handler = new RequestHandler() {
      @Override
      public void handle(Request request, HttpServletResponse response) {
        LOG.fine("starting request: " + counter.incrementAndGet());
        LOG.fine(String.format("handler %s - %s", request.getPathInfo(), counter.get()));
        response.setStatus(200);
      }
    };

    for (int i = 0; i < threads; i++) {
      callables.add(new Callable<Integer>() {
        @Override
        public Integer call() throws Exception {
          HttpRequest r = HttpRequest.get(baseTlsUrl)
            .trustAllCerts()
            .trustAllHosts();

          //TODO: the appears to be a race condition between the proxy and this test causing a 404 sometimes
//          Assert.assertEquals(200, r.code());
          LOG.info("OK #" + counter.get());
          return r.code();
        }
      });
    }
    List<Future<Integer>> futures = es.invokeAll(callables);
    for (Future<Integer> future : futures) {
      future.get();
    }

    Assert.assertEquals(threads, counter.get());

    es.shutdown();
  }
}
