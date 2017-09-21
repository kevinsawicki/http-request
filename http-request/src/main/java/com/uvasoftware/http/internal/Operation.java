package com.uvasoftware.http.internal;

import com.uvasoftware.http.HttpRequestException;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Operation that handles executing a callback once complete and handling
 * nested exceptions
 *
 * @param <V>
 */
public abstract class Operation<V> implements Callable<V> {

  /**
   * Run operation
   *
   * @return result
   * @throws HttpRequestException
   * @throws IOException
   */
  protected abstract V run() throws HttpRequestException, IOException;

  /**
   * Operation complete callback
   *
   * @throws IOException
   */
  protected abstract void done() throws IOException;

  public V call() throws HttpRequestException {
    boolean thrown = false;
    try {
      return run();
    } catch (HttpRequestException e) {
      thrown = true;
      throw e;
    } catch (IOException e) {
      thrown = true;
      throw new HttpRequestException(e);
    } finally {
      try {
        done();
      } catch (IOException e) {
        if (!thrown)
          throw new HttpRequestException(e);
      }
    }
  }
}
