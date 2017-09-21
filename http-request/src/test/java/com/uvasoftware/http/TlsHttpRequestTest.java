package com.uvasoftware.http;

import org.eclipse.jetty.server.Request;
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

public class TlsHttpRequestTest extends HttpRequestTest {
  @Test
  public void shouldBeThreadSafe() throws Exception {
    handler = new RequestHandler() {
      @Override
      public void handle(Request request, HttpServletResponse response) {
        response.setStatus(200);
      }
    };

    final int threads = 25;

    ExecutorService es = Executors.newFixedThreadPool(threads);
    List<Callable<Integer>> callables = new ArrayList<Callable<Integer>>();

    final AtomicInteger counter = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      callables.add(new Callable<Integer>() {
        @Override
        public Integer call() throws Exception {
          HttpRequest r = HttpRequest.get("https://localhost:8081")
            .trustAllCerts()
            .trustAllHosts();

          Assert.assertEquals(200, r.code());
          System.out.println(r.message() + " #" + counter.incrementAndGet());
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
