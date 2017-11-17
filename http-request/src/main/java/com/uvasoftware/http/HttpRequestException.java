package com.uvasoftware.http;

import java.io.IOException;

/**
 * HTTP request exception whose cause is always an {@link IOException}
 */
public class HttpRequestException extends RuntimeException {

  private static final long serialVersionUID = -1170466989781746231L;

  /**
   * Create a new HttpRequestException with the given cause
   **/
  public HttpRequestException(final IOException cause) {
    super(cause);
  }

  /**
   * Get {@link IOException} that triggered this request exception
   *
   * @return {@link IOException} cause
   */
  @Override
  public IOException getCause() {
    return (IOException) super.getCause();
  }
}
