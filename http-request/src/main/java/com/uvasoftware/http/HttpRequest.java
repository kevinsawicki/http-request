package com.uvasoftware.http;

import com.uvasoftware.http.internal.Base64;
import com.uvasoftware.http.internal.CloseOperation;
import com.uvasoftware.http.internal.FlushOperation;
import com.uvasoftware.http.internal.HttpUtils;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static com.uvasoftware.http.internal.HttpUtils.append;
import static com.uvasoftware.http.internal.HttpUtils.encode;
import static java.net.HttpURLConnection.*;
import static java.net.Proxy.Type.HTTP;

/**
 * A fluid interface for making HTTP requests using an underlying
 * {@link HttpURLConnection} (or sub-class).
 * <p>
 * Each instance supports making a single request and cannot be reused for
 * further requests.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class HttpRequest {

  /**
   * 'UTF-8' charset name
   */
  public static final String CHARSET_UTF8 = "UTF-8";

  /**
   * 'application/x-www-form-urlencoded' content type header value
   */
  public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

  /**
   * 'application/json' content type header value
   */
  public static final String CONTENT_TYPE_JSON = "application/json";

  /**
   * 'gzip' encoding header value
   */
  public static final String ENCODING_GZIP = "gzip";

  /**
   * 'Accept' header name
   */
  public static final String HEADER_ACCEPT = "Accept";

  /**
   * 'Accept-Charset' header name
   */
  public static final String HEADER_ACCEPT_CHARSET = "Accept-Charset";

  /**
   * 'Accept-Encoding' header name
   */
  public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";

  /**
   * 'Authorization' header name
   */
  public static final String HEADER_AUTHORIZATION = "Authorization";

  /**
   * 'Cache-Control' header name
   */
  public static final String HEADER_CACHE_CONTROL = "Cache-Control";

  /**
   * 'Content-Encoding' header name
   */
  public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";

  /**
   * 'Content-Length' header name
   */
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";

  /**
   * 'Content-Type' header name
   */
  public static final String HEADER_CONTENT_TYPE = "Content-Type";

  /**
   * 'Date' header name
   */
  public static final String HEADER_DATE = "Date";

  /**
   * 'ETag' header name
   */
  public static final String HEADER_ETAG = "ETag";

  /**
   * 'Expires' header name
   */
  public static final String HEADER_EXPIRES = "Expires";

  /**
   * 'If-None-Match' header name
   */
  public static final String HEADER_IF_NONE_MATCH = "If-None-Match";

  /**
   * 'Last-Modified' header name
   */
  public static final String HEADER_LAST_MODIFIED = "Last-Modified";

  /**
   * 'Location' header name
   */
  public static final String HEADER_LOCATION = "Location";

  /**
   * 'Proxy-Authorization' header name
   */
  public static final String HEADER_PROXY_AUTHORIZATION = "Proxy-Authorization";

  /**
   * 'Referer' header name
   */
  public static final String HEADER_REFERER = "Referer";

  /**
   * 'Server' header name
   */
  public static final String HEADER_SERVER = "Server";

  /**
   * 'User-Agent' header name
   */
  public static final String HEADER_USER_AGENT = "User-Agent";

  /**
   * 'DELETE' request method
   */
  public static final String METHOD_DELETE = "DELETE";

  /**
   * 'GET' request method
   */
  public static final String METHOD_GET = "GET";

  /**
   * 'HEAD' request method
   */
  public static final String METHOD_HEAD = "HEAD";

  /**
   * 'OPTIONS' options method
   */
  public static final String METHOD_OPTIONS = "OPTIONS";

  /**
   * 'POST' request method
   */
  public static final String METHOD_POST = "POST";

  /**
   * 'PUT' request method
   */
  public static final String METHOD_PUT = "PUT";

  /**
   * 'TRACE' request method
   */
  public static final String METHOD_TRACE = "TRACE";

  /**
   * 'charset' header value parameter
   */
  public static final String PARAM_CHARSET = "charset";

  private static final Logger LOG = Logger.getLogger(HttpRequest.class.getName());

  private static final String BOUNDARY = "00content0boundary00";
  private static final String CONTENT_TYPE_MULTIPART = "multipart/form-data; boundary=" + BOUNDARY;
  private static final String CRLF = "\r\n";
  private static final String[] EMPTY_STRINGS = new String[0];

  private static final ThreadLocal<SSLSocketFactory> trustedFactory = new ThreadLocal<SSLSocketFactory>();
  private static HostnameVerifier trustedVerifier;
  private static volatile ConnectionFactory connectionFactory = ConnectionFactory.DEFAULT;
  private final URL url;
  private final String requestMethod;
  private HttpURLConnection connection = null;
  private RequestOutputStream output;
  private boolean multipart;
  private boolean form;
  private boolean ignoreCloseExceptions = true;
  private boolean uncompressed = false;
  private int bufferSize = 8192;
  private long totalSize = -1;
  private long totalWritten = 0;
  private String httpProxyHost;
  private int httpProxyPort;
  private UploadProgress progress = UploadProgress.DEFAULT;

  /**
   * Create HTTP connection wrapper
   *
   * @param url    Remote resource URL.
   * @param method HTTP request method (e.g., "GET", "POST").
   * @
   */
  public HttpRequest(final CharSequence url, final String method) {
    try {
      this.url = new URL(url.toString());
    } catch (MalformedURLException e) {
      throw new HttpRequestException(e);
    }
    this.requestMethod = method;

    LOG.fine(String.format("Request -> [%s] %s ", method, url));
  }

  /**
   * Create HTTP connection wrapper
   *
   * @param url    Remote resource URL.
   * @param method HTTP request method (e.g., "GET", "POST").
   * @
   */
  public HttpRequest(final URL url, final String method) {
    this.url = url;
    this.requestMethod = method;
  }

  private static String getValidCharset(final String charset) {
    if (charset != null && charset.length() > 0)
      return charset;
    else
      return CHARSET_UTF8;
  }

  private static SSLSocketFactory getTrustedFactory() {
    if (trustedFactory.get() == null) {
      final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {

        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
          // Intentionally left blank
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
          // Intentionally left blank
        }
      }};
      try {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAllCerts, new SecureRandom());
        trustedFactory.set(context.getSocketFactory());
      } catch (GeneralSecurityException e) {
        IOException ioException = new IOException(
          "Security exception configuring SSL context", e);
        throw new HttpRequestException(ioException);
      }
    }

    return trustedFactory.get();
  }

  private static HostnameVerifier getTrustedVerifier() {
    if (trustedVerifier == null)
      trustedVerifier = new HostnameVerifier() {

        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

    return trustedVerifier;
  }


  /**
   * Specify the {@link ConnectionFactory} used to create new requests.
   */
  public static void setConnectionFactory(final ConnectionFactory connectionFactory) {
    if (connectionFactory == null)
      HttpRequest.connectionFactory = ConnectionFactory.DEFAULT;
    else
      HttpRequest.connectionFactory = connectionFactory;
  }


  /**
   * Start a 'GET' request to the given URL
   *
   * @param url request destination URL
   * @return request
   */
  public static HttpRequest get(final CharSequence url) {
    return new HttpRequest(url, METHOD_GET);
  }

  /**
   * Start a 'GET' request to the given URL
   *
   * @param url request destination URL
   * @return request
   */
  public static HttpRequest get(final URL url) {
    return new HttpRequest(url, METHOD_GET);
  }

  /**
   * Start a 'GET' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param params  The query parameters to include as part of the baseUrl
   * @param encode  true to encode the full URL
   * @return request
   */
  public static HttpRequest get(final CharSequence baseUrl,
                                final Map<?, ?> params, final boolean encode) {
    String url = append(baseUrl, params);
    return get(encode ? encode(url) : url);
  }

  /**
   * Start a 'GET' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param encode  true to encode the full URL
   * @param params  the name/value query parameter pairs to include as part of the
   *                baseUrl
   * @return request
   */
  public static HttpRequest get(final CharSequence baseUrl,
                                final boolean encode, final Object... params) {
    String url = append(baseUrl, params);
    return get(encode ? encode(url) : url);
  }

  /**
   * Start a 'POST' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest post(final CharSequence url) {
    return new HttpRequest(url, METHOD_POST);
  }

  /**
   * Start a 'POST' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest post(final URL url) {
    return new HttpRequest(url, METHOD_POST);
  }

  /**
   * Start a 'POST' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param params  the query parameters to include as part of the baseUrl
   * @param encode  true to encode the full URL
   * @return request
   */
  public static HttpRequest post(final CharSequence baseUrl,
                                 final Map<?, ?> params, final boolean encode) {
    String url = append(baseUrl, params);
    return post(encode ? encode(url) : url);
  }

  /**
   * Start a 'POST' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param encode  true to encode the full URL
   * @param params  the name/value query parameter pairs to include as part of the
   *                baseUrl
   * @return request
   */
  public static HttpRequest post(final CharSequence baseUrl,
                                 final boolean encode, final Object... params) {
    String url = append(baseUrl, params);
    return post(encode ? encode(url) : url);
  }

  /**
   * Start a 'PUT' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest put(final CharSequence url) {
    return new HttpRequest(url, METHOD_PUT);
  }

  /**
   * Start a 'PUT' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest put(final URL url) {
    return new HttpRequest(url, METHOD_PUT);
  }

  /**
   * Start a 'PUT' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param params  the query parameters to include as part of the baseUrl
   * @param encode  true to encode the full URL
   * @return request
   */
  public static HttpRequest put(final CharSequence baseUrl,
                                final Map<?, ?> params, final boolean encode) {
    String url = append(baseUrl, params);
    return put(encode ? encode(url) : url);
  }

  /**
   * Start a 'PUT' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param encode  true to encode the full URL
   * @param params  the name/value query parameter pairs to include as part of the
   *                baseUrl
   * @return request
   */
  public static HttpRequest put(final CharSequence baseUrl,
                                final boolean encode, final Object... params) {
    String url = append(baseUrl, params);
    return put(encode ? encode(url) : url);
  }

  /**
   * Start a 'DELETE' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest delete(final CharSequence url) {
    return new HttpRequest(url, METHOD_DELETE);
  }

  /**
   * Start a 'DELETE' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest delete(final URL url) {
    return new HttpRequest(url, METHOD_DELETE);
  }

  /**
   * Start a 'DELETE' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param params  The query parameters to include as part of the baseUrl
   * @param encode  true to encode the full URL
   * @return request
   */
  public static HttpRequest delete(final CharSequence baseUrl,
                                   final Map<?, ?> params, final boolean encode) {
    String url = append(baseUrl, params);
    return delete(encode ? encode(url) : url);
  }

  /**
   * Start a 'DELETE' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param encode  true to encode the full URL
   * @param params  the name/value query parameter pairs to include as part of the
   *                baseUrl
   * @return request
   */
  public static HttpRequest delete(final CharSequence baseUrl,
                                   final boolean encode, final Object... params) {
    String url = append(baseUrl, params);
    return delete(encode ? encode(url) : url);
  }

  /**
   * Start a 'HEAD' request to the given URL
   *
   * @param url
   * @return request
   * @
   */
  public static HttpRequest head(final CharSequence url) {
    return new HttpRequest(url, METHOD_HEAD);
  }

  /**
   * Start a 'HEAD' request to the given URL
   *
   * @param url
   * @return request
   * @
   */
  public static HttpRequest head(final URL url) {
    return new HttpRequest(url, METHOD_HEAD);
  }

  /**
   * Start a 'HEAD' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param params  The query parameters to include as part of the baseUrl
   * @param encode  true to encode the full URL
   * @return request
   */
  public static HttpRequest head(final CharSequence baseUrl,
                                 final Map<?, ?> params, final boolean encode) {
    String url = append(baseUrl, params);
    return head(encode ? encode(url) : url);
  }

  /**
   * Start a 'GET' request to the given URL along with the query params
   *
   * @param baseUrl request destination base URL
   * @param encode  true to encode the full URL
   * @param params  the name/value query parameter pairs to include as part of the
   *                baseUrl
   * @return request
   */
  public static HttpRequest head(final CharSequence baseUrl,
                                 final boolean encode, final Object... params) {
    String url = append(baseUrl, params);
    return head(encode ? encode(url) : url);
  }

  /**
   * Start an 'OPTIONS' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest options(final CharSequence url) {
    return new HttpRequest(url, METHOD_OPTIONS);
  }

  /**
   * Start an 'OPTIONS' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest options(final URL url) {
    return new HttpRequest(url, METHOD_OPTIONS);
  }

  /**
   * Start a 'TRACE' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest trace(final CharSequence url) {
    return new HttpRequest(url, METHOD_TRACE);
  }

  /**
   * Start a 'TRACE' request to the given URL
   *
   * @param url request destination URL
   * @return request
   * @
   */
  public static HttpRequest trace(final URL url) {
    return new HttpRequest(url, METHOD_TRACE);
  }

  /**
   * Set the 'http.keepAlive' property to the given value.
   * <p>
   * This setting will apply to all requests.
   *
   * @param keepAlive
   */
  public static void keepAlive(final boolean keepAlive) {
    setProperty("http.keepAlive", Boolean.toString(keepAlive));
  }

  /**
   * Set the 'http.maxConnections' property to the given value.
   * <p>
   * This setting will apply to all requests.
   *
   * @param maxConnections
   */
  public static void maxConnections(final int maxConnections) {
    setProperty("http.maxConnections", Integer.toString(maxConnections));
  }

  /**
   * Set the 'http.proxyHost' and 'https.proxyHost' properties to the given host
   * value.
   * <p>
   * This setting will apply to all requests.
   *
   * @param host
   */
  public static void proxyHost(final String host) {
    setProperty("http.proxyHost", host);
    setProperty("https.proxyHost", host);
  }

  /**
   * Set the 'http.proxyPort' and 'https.proxyPort' properties to the given port
   * number.
   * <p>
   * This setting will apply to all requests.
   *
   * @param port
   */
  public static void proxyPort(final int port) {
    final String portValue = Integer.toString(port);
    setProperty("http.proxyPort", portValue);
    setProperty("https.proxyPort", portValue);
  }

  /**
   * Set the 'http.nonProxyHosts' property to the given host values.
   * <p>
   * Hosts will be separated by a '|' character.
   * <p>
   * This setting will apply to all requests.
   *
   * @param hosts
   */
  public static void nonProxyHosts(final String... hosts) {
    if (hosts != null && hosts.length > 0) {
      StringBuilder separated = new StringBuilder();
      int last = hosts.length - 1;
      for (int i = 0; i < last; i++)
        separated.append(hosts[i]).append('|');
      separated.append(hosts[last]);
      setProperty("http.nonProxyHosts", separated.toString());
    } else
      setProperty("http.nonProxyHosts", null);
  }

  /**
   * Set property to given value.
   * <p>
   * Specifying a null value will cause the property to be cleared
   *
   * @param name
   * @param value
   * @return previous value
   */
  private static String setProperty(final String name, final String value) {
    final PrivilegedAction<String> action;
    if (value != null)
      action = new PrivilegedAction<String>() {

        public String run() {
          return System.setProperty(name, value);
        }
      };
    else
      action = new PrivilegedAction<String>() {

        public String run() {
          return System.clearProperty(name);
        }
      };
    return AccessController.doPrivileged(action);
  }

  /**
   * Write the json string
   *
   * @param jsonString the json payload to send
   * @return this request
   * @
   */
  public HttpRequest json(final CharSequence jsonString) {
    contentType(CONTENT_TYPE_JSON);
    send(jsonString);
    return this;
  }

  private Proxy createProxy() {
    return new Proxy(HTTP, new InetSocketAddress(httpProxyHost, httpProxyPort));
  }

  private HttpURLConnection createConnection() {
    try {
      final HttpURLConnection connection;
      if (httpProxyHost != null)
        connection = connectionFactory.create(url, createProxy());
      else
        connection = connectionFactory.create(url);
      connection.setRequestMethod(requestMethod);
      return connection;
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  @Override
  public String toString() {
    return method() + ' ' + url();
  }

  /**
   * Get underlying connection
   *
   * @return connection
   */
  public HttpURLConnection getConnection() {
    if (connection == null)
      connection = createConnection();
    return connection;
  }

  /**
   * Set whether or not to ignore exceptions that occur from calling
   * {@link Closeable#close()}
   * <p>
   * The default value of this setting is <code>true</code>
   *
   * @param ignore whether it should be ignored or not
   * @return this request
   */
  public HttpRequest ignoreCloseExceptions(final boolean ignore) {
    ignoreCloseExceptions = ignore;
    return this;
  }

  /**
   * Get whether or not exceptions thrown by {@link Closeable#close()} are
   * ignored
   *
   * @return true if ignoring, false if throwing
   */
  public boolean ignoreCloseExceptions() {
    return ignoreCloseExceptions;
  }

  /**
   * Get the status code of the response
   *
   * @return the response code
   */
  public int code() {
    try {
      closeOutput();
      return getConnection().getResponseCode();
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Set the value of the given {@link AtomicInteger} to the status code of the
   * response
   *
   * @param output the atomic integer to host the response code
   * @return this request
   */
  public HttpRequest code(final AtomicInteger output) {
    output.set(code());
    return this;
  }

  /**
   * Is the response code a 200 OK?
   *
   * @return true if 200, false otherwise
   */
  public boolean ok() {
    return HTTP_OK == code();
  }

  /**
   * Is the response code a 201 Created?
   *
   * @return true if 201, false otherwise
   * @
   */
  public boolean created() {
    return HTTP_CREATED == code();
  }

  /**
   * Is the response code a 204 No Content?
   *
   * @return true if 204, false otherwise
   * @
   */
  public boolean noContent() {
    return HTTP_NO_CONTENT == code();
  }

  /**
   * Is the response code a 500 Internal Server Error?
   *
   * @return true if 500, false otherwise
   * @
   */
  public boolean serverError() {
    return HTTP_INTERNAL_ERROR == code();
  }

  /**
   * Is the response code a 400 Bad Request?
   *
   * @return true if 400, false otherwise
   * @
   */
  public boolean badRequest() {
    return HTTP_BAD_REQUEST == code();
  }

  /**
   * Is the response code a 404 Not Found?
   *
   * @return true if 404, false otherwise
   * @
   */
  public boolean notFound() {
    return HTTP_NOT_FOUND == code();
  }

  /**
   * Is the response code a 304 Not Modified?
   *
   * @return true if 304, false otherwise
   * @
   */
  public boolean notModified() {
    return HTTP_NOT_MODIFIED == code();
  }

  /**
   * Get status message of the response
   *
   * @return message
   * @
   */
  public String message() {
    try {
      closeOutput();
      return getConnection().getResponseMessage();
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Disconnect the connection
   *
   * @return this request
   */
  public HttpRequest disconnect() {
    getConnection().disconnect();
    return this;
  }

  /**
   * Set chunked streaming mode to the given size
   *
   * @param size
   * @return this request
   */
  public HttpRequest chunk(final int size) {
    getConnection().setChunkedStreamingMode(size);
    return this;
  }

  /**
   * Set the size used when buffering and copying between streams
   * <p>
   * This size is also used for send and receive buffers created for both char
   * and byte arrays
   * <p>
   * The default buffer size is 8,192 bytes
   *
   * @param size
   * @return this request
   */
  public HttpRequest bufferSize(final int size) {
    if (size < 1)
      throw new IllegalArgumentException("Size must be greater than zero");
    bufferSize = size;
    return this;
  }

  /**
   * Get the configured buffer size
   * <p>
   * The default buffer size is 8,192 bytes
   *
   * @return buffer size
   */
  public int bufferSize() {
    return bufferSize;
  }

  /**
   * Set whether or not the response body should be automatically uncompressed
   * when read from.
   * <p>
   * This will only affect requests that have the 'Content-Encoding' response
   * header set to 'gzip'.
   * <p>
   * This causes all receive methods to use a {@link GZIPInputStream} when
   * applicable so that higher level streams and readers can read the data
   * uncompressed.
   * <p>
   * Setting this option does not cause any request headers to be set
   * automatically so {@link #acceptGzipEncoding()} should be used in
   * conjunction with this setting to tell the server to gzip the response.
   *
   * @param uncompress
   * @return this request
   */
  public HttpRequest uncompress(final boolean uncompress) {
    this.uncompressed = uncompress;
    return this;
  }

  /**
   * Create byte array output stream
   *
   * @return stream
   */
  protected ByteArrayOutputStream byteStream() {
    final int size = contentLength();
    if (size > 0)
      return new ByteArrayOutputStream(size);
    else
      return new ByteArrayOutputStream();
  }

  /**
   * Get response as {@link String} in given character set
   * <p>
   * This will fall back to using the UTF-8 character set if the given charset
   * is null
   *
   * @param charset
   * @return string
   * @
   */
  public String body(final String charset) {
    final ByteArrayOutputStream output = byteStream();
    try {
      copy(buffer(), output);
      return output.toString(getValidCharset(charset));
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Get response as {@link String} using character set returned from
   * {@link #charset()}
   *
   * @return string
   * @
   */
  public String body() {
    return body(charset());
  }

  /**
   * Get the response body as a {@link String} and set it as the value of the
   * given reference.
   *
   * @param output
   * @return this request
   * @
   */
  public HttpRequest body(final AtomicReference<String> output) {
    output.set(body());
    return this;
  }

  /**
   * Get the response body as a {@link String} and set it as the value of the
   * given reference.
   *
   * @param output
   * @param charset
   * @return this request
   * @
   */
  public HttpRequest body(final AtomicReference<String> output, final String charset) {
    output.set(body(charset));
    return this;
  }

  /**
   * Is the response body empty?
   *
   * @return true if the Content-Length response header is 0, false otherwise
   * @
   */
  public boolean isBodyEmpty() {
    return contentLength() == 0;
  }

  /**
   * Get response as byte array
   *
   * @return byte array
   * @
   */
  public byte[] bytes() {
    final ByteArrayOutputStream output = byteStream();
    try {
      copy(buffer(), output);
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return output.toByteArray();
  }

  /**
   * Get response in a buffered stream
   *
   * @return stream
   * @
   * @see #bufferSize(int)
   */
  public BufferedInputStream buffer() {
    return new BufferedInputStream(stream(), bufferSize);
  }

  /**
   * Get stream to response body
   *
   * @return stream
   * @
   */
  public InputStream stream() {
    InputStream stream;
    if (code() < HTTP_BAD_REQUEST)
      try {
        stream = getConnection().getInputStream();
      } catch (IOException e) {
        throw new HttpRequestException(e);
      }
    else {
      stream = getConnection().getErrorStream();
      if (stream == null)
        try {
          stream = getConnection().getInputStream();
        } catch (IOException e) {
          if (contentLength() > 0)
            throw new HttpRequestException(e);
          else
            stream = new ByteArrayInputStream(new byte[0]);
        }
    }

    if (!uncompressed || !ENCODING_GZIP.equals(contentEncoding()))
      return stream;
    else
      try {
        return new GZIPInputStream(stream);
      } catch (IOException e) {
        throw new HttpRequestException(e);
      }
  }

  /**
   * Get reader to response body using given character set.
   * <p>
   * This will fall back to using the UTF-8 character set if the given charset
   * is null
   *
   * @param charset
   * @return reader
   */
  public InputStreamReader reader(final String charset) {
    try {
      return new InputStreamReader(stream(), getValidCharset(charset));
    } catch (UnsupportedEncodingException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Get reader to response body using the character set returned from
   * {@link #charset()}
   *
   * @return reader
   * @
   */
  public InputStreamReader reader() {
    return reader(charset());
  }

  /**
   * Get buffered reader to response body using the given character set r and
   * the configured buffer size
   *
   * @param charset
   * @return reader
   * @see #bufferSize(int)
   */
  public BufferedReader bufferedReader(final String charset) {
    return new BufferedReader(reader(charset), bufferSize);
  }

  /**
   * Get buffered reader to response body using the character set returned from
   * {@link #charset()} and the configured buffer size
   *
   * @return reader
   * @see #bufferSize(int)
   */
  public BufferedReader bufferedReader() {
    return bufferedReader(charset());
  }

  /**
   * Stream response body to file
   *
   * @param file
   * @return this request
   */
  public HttpRequest receive(final File file) {
    final OutputStream output;
    try {
      output = new BufferedOutputStream(new FileOutputStream(file), bufferSize);
    } catch (FileNotFoundException e) {
      throw new HttpRequestException(e);
    }
    return new CloseOperation<HttpRequest>(output, ignoreCloseExceptions) {

      @Override
      protected HttpRequest run() throws IOException {
        return receive(output);
      }
    }.call();
  }

  /**
   * Stream response to given output stream
   *
   * @param output
   * @return this request
   */
  public HttpRequest receive(final OutputStream output) {
    try {
      return copy(buffer(), output);
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Stream response to given print stream
   *
   * @param output
   * @return this request
   */
  public HttpRequest receive(final PrintStream output) {
    return receive((OutputStream) output);
  }

  /**
   * Receive response into the given appendable
   *
   * @param appendable
   * @return this request
   */
  public HttpRequest receive(final Appendable appendable) {
    final BufferedReader reader = bufferedReader();
    return new CloseOperation<HttpRequest>(reader, ignoreCloseExceptions) {

      @Override
      public HttpRequest run() throws IOException {
        final CharBuffer buffer = CharBuffer.allocate(bufferSize);
        int read;
        while ((read = reader.read(buffer)) != -1) {
          buffer.rewind();
          appendable.append(buffer, 0, read);
          buffer.rewind();
        }
        return HttpRequest.this;
      }
    }.call();
  }

  /**
   * Receive response into the given writer
   *
   * @param writer
   * @return this request
   */
  public HttpRequest receive(final Writer writer) {
    final BufferedReader reader = bufferedReader();
    return new CloseOperation<HttpRequest>(reader, ignoreCloseExceptions) {

      @Override
      public HttpRequest run() throws IOException {
        return copy(reader, writer);
      }
    }.call();
  }

  /**
   * Set read timeout on connection to given value
   *
   * @param timeout
   * @return this request
   */
  public HttpRequest readTimeout(final int timeout) {
    getConnection().setReadTimeout(timeout);
    return this;
  }

  /**
   * Set connect timeout on connection to given value
   *
   * @param timeout
   * @return this request
   */
  public HttpRequest connectTimeout(final int timeout) {
    getConnection().setConnectTimeout(timeout);
    return this;
  }

  /**
   * Set header name to given value
   *
   * @param name  the header name to set
   * @param value the header value to set
   * @return this request
   */
  public HttpRequest header(final String name, final String value) {
    String sanitizedValue = value;
    if (name == null) {
      throw new IllegalStateException("header name cannot be null");
    }

    if (value == null) {
      LOG.warning("header value received, casting it to empty string");
      sanitizedValue = "";
    }

    getConnection().setRequestProperty(name, sanitizedValue);
    return this;
  }

  /**
   * Set header name to given value
   *
   * @param name  the header name to set
   * @param value the header value to set
   * @return this request
   */
  public HttpRequest header(final String name, final Number value) {
    return header(name, value != null ? value.toString() : null);
  }

  /**
   * Set all headers found in given map where the keys are the header names and
   * the values are the header values
   *
   * @param headers
   * @return this request
   */
  public HttpRequest headers(final Map<String, String> headers) {
    if (!headers.isEmpty())
      for (Entry<String, String> header : headers.entrySet())
        header(header);
    return this;
  }

  /**
   * Set header to have given entry's key as the name and value as the value
   *
   * @param header
   * @return this request
   */
  public HttpRequest header(final Entry<String, String> header) {
    return header(header.getKey(), header.getValue());
  }

  /**
   * Get a response header
   *
   * @param name
   * @return response header
   * @
   */
  public String header(final String name) {
    closeOutputQuietly();
    return getConnection().getHeaderField(name);
  }

  /**
   * Get all the response headers
   *
   * @return map of response header names to their value(s)
   * @
   */
  public Map<String, List<String>> headers() {
    closeOutputQuietly();
    return getConnection().getHeaderFields();
  }

  /**
   * Get a date header from the response falling back to returning -1 if the
   * header is missing or parsing fails
   *
   * @param name
   * @return date, -1 on failures
   * @
   */
  public long dateHeader(final String name) {
    return dateHeader(name, -1L);
  }

  /**
   * Get a date header from the response falling back to returning the given
   * default value if the header is missing or parsing fails
   *
   * @param name
   * @param defaultValue
   * @return date, default value on failures
   * @
   */
  public long dateHeader(final String name, final long defaultValue) {
    closeOutputQuietly();
    return getConnection().getHeaderFieldDate(name, defaultValue);
  }

  /**
   * Get an integer header from the response falling back to returning -1 if the
   * header is missing or parsing fails
   *
   * @param name
   * @return header value as an integer, -1 when missing or parsing fails
   * @
   */
  public int intHeader(final String name) {
    return intHeader(name, -1);
  }

  /**
   * Get an integer header value from the response falling back to the given
   * default value if the header is missing or if parsing fails
   *
   * @param name
   * @param defaultValue
   * @return header value as an integer, default value when missing or parsing
   * fails
   * @
   */
  public int intHeader(final String name, final int defaultValue) {
    closeOutputQuietly();
    return getConnection().getHeaderFieldInt(name, defaultValue);
  }

  /**
   * Get all values of the given header from the response
   *
   * @param name
   * @return non-null but possibly empty array of {@link String} header values
   */
  public String[] headers(final String name) {
    final Map<String, List<String>> headers = headers();
    if (headers == null || headers.isEmpty())
      return EMPTY_STRINGS;

    final List<String> values = headers.get(name);
    if (values != null && !values.isEmpty())
      return values.toArray(new String[values.size()]);
    else
      return EMPTY_STRINGS;
  }

  /**
   * Get parameter with given name from header value in response
   *
   * @param headerName
   * @param paramName
   * @return parameter value or null if missing
   */
  public String parameter(final String headerName, final String paramName) {
    return HttpUtils.getParam(header(headerName), paramName);
  }

  /**
   * Get all parameters from header value in response
   * <p>
   * This will be all key=value pairs after the first ';' that are separated by
   * a ';'
   *
   * @param headerName
   * @return non-null but possibly empty map of parameter headers
   */
  public Map<String, String> parameters(final String headerName) {
    return HttpUtils.getParams(header(headerName));
  }


  /**
   * Get 'charset' parameter from 'Content-Type' response header
   *
   * @return charset or null if none
   */
  public String charset() {
    return parameter(HEADER_CONTENT_TYPE, PARAM_CHARSET);
  }

  /**
   * Set the 'User-Agent' header to given value
   *
   * @param userAgent
   * @return this request
   */
  public HttpRequest userAgent(final String userAgent) {
    return header(HEADER_USER_AGENT, userAgent);
  }

  /**
   * Set the 'Referer' header to given value
   *
   * @param referer
   * @return this request
   */
  public HttpRequest referer(final String referer) {
    return header(HEADER_REFERER, referer);
  }

  /**
   * Set value of {@link HttpURLConnection#setUseCaches(boolean)}
   *
   * @param useCaches
   * @return this request
   */
  public HttpRequest useCaches(final boolean useCaches) {
    getConnection().setUseCaches(useCaches);
    return this;
  }

  /**
   * Set the 'Accept-Encoding' header to given value
   *
   * @param acceptEncoding
   * @return this request
   */
  public HttpRequest acceptEncoding(final String acceptEncoding) {
    return header(HEADER_ACCEPT_ENCODING, acceptEncoding);
  }

  /**
   * Set the 'Accept-Encoding' header to 'gzip'
   *
   * @return this request
   * @see #uncompress(boolean)
   */
  public HttpRequest acceptGzipEncoding() {
    return acceptEncoding(ENCODING_GZIP);
  }

  /**
   * Set the 'Accept-Charset' header to given value
   *
   * @param acceptCharset
   * @return this request
   */
  public HttpRequest acceptCharset(final String acceptCharset) {
    return header(HEADER_ACCEPT_CHARSET, acceptCharset);
  }

  /**
   * Get the 'Content-Encoding' header from the response
   *
   * @return this request
   */
  public String contentEncoding() {
    return header(HEADER_CONTENT_ENCODING);
  }

  /**
   * Get the 'Server' header from the response
   *
   * @return server
   */
  public String server() {
    return header(HEADER_SERVER);
  }

  /**
   * Get the 'Date' header from the response
   *
   * @return date value, -1 on failures
   */
  public long date() {
    return dateHeader(HEADER_DATE);
  }

  /**
   * Get the 'Cache-Control' header from the response
   *
   * @return cache control
   */
  public String cacheControl() {
    return header(HEADER_CACHE_CONTROL);
  }

  /**
   * Get the 'ETag' header from the response
   *
   * @return entity tag
   */
  public String eTag() {
    return header(HEADER_ETAG);
  }

  /**
   * Get the 'Expires' header from the response
   *
   * @return expires value, -1 on failures
   */
  public long expires() {
    return dateHeader(HEADER_EXPIRES);
  }

  /**
   * Get the 'Last-Modified' header from the response
   *
   * @return last modified value, -1 on failures
   */
  public long lastModified() {
    return dateHeader(HEADER_LAST_MODIFIED);
  }

  /**
   * Get the 'Location' header from the response
   *
   * @return location
   */
  public String location() {
    return header(HEADER_LOCATION);
  }

  /**
   * Set the 'Authorization' header to given value
   *
   * @param authorization
   * @return this request
   */
  public HttpRequest authorization(final String authorization) {
    return header(HEADER_AUTHORIZATION, authorization);
  }

  /**
   * Set the 'Proxy-Authorization' header to given value
   *
   * @param proxyAuthorization
   * @return this request
   */
  public HttpRequest proxyAuthorization(final String proxyAuthorization) {
    return header(HEADER_PROXY_AUTHORIZATION, proxyAuthorization);
  }

  /**
   * Set the 'Authorization' header to given values in Basic authentication
   * format
   *
   * @param name
   * @param password
   * @return this request
   */
  public HttpRequest basic(final String name, final String password) {
    return authorization("Basic " + com.uvasoftware.http.internal.Base64.encode(name + ':' + password));
  }

  /**
   * Set the 'Proxy-Authorization' header to given values in Basic authentication
   * format
   *
   * @param name
   * @param password
   * @return this request
   */
  public HttpRequest proxyBasic(final String name, final String password) {
    return proxyAuthorization("Basic " + Base64.encode(name + ':' + password));
  }

  /**
   * Set the 'If-Modified-Since' request header to the given value
   *
   * @param ifModifiedSince
   * @return this request
   */
  public HttpRequest ifModifiedSince(final long ifModifiedSince) {
    getConnection().setIfModifiedSince(ifModifiedSince);
    return this;
  }

  /**
   * Set the 'If-None-Match' request header to the given value
   *
   * @param ifNoneMatch
   * @return this request
   */
  public HttpRequest ifNoneMatch(final String ifNoneMatch) {
    return header(HEADER_IF_NONE_MATCH, ifNoneMatch);
  }

  /**
   * Set the 'Content-Type' request header to the given value
   *
   * @param contentType
   * @return this request
   */
  public HttpRequest contentType(final String contentType) {
    return contentType(contentType, null);
  }

  /**
   * Set the 'Content-Type' request header to the given value and charset
   *
   * @param contentType
   * @param charset
   * @return this request
   */
  public HttpRequest contentType(final String contentType, final String charset) {
    if (charset != null && charset.length() > 0) {
      final String separator = "; " + PARAM_CHARSET + '=';
      return header(HEADER_CONTENT_TYPE, contentType + separator + charset);
    } else
      return header(HEADER_CONTENT_TYPE, contentType);
  }

  /**
   * Get the 'Content-Type' header from the response
   *
   * @return response header value
   */
  public String contentType() {
    return header(HEADER_CONTENT_TYPE);
  }

  /**
   * Get the 'Content-Length' header from the response
   *
   * @return response header value
   */
  public int contentLength() {
    return intHeader(HEADER_CONTENT_LENGTH);
  }

  /**
   * Set the 'Content-Length' request header to the given value
   *
   * @param contentLength
   * @return this request
   */
  public HttpRequest contentLength(final String contentLength) {
    return contentLength(Integer.parseInt(contentLength));
  }

  /**
   * Set the 'Content-Length' request header to the given value
   *
   * @param contentLength
   * @return this request
   */
  public HttpRequest contentLength(final int contentLength) {
    getConnection().setFixedLengthStreamingMode(contentLength);
    return this;
  }

  /**
   * Set the 'Accept' header to given value
   *
   * @param accept
   * @return this request
   */
  public HttpRequest accept(final String accept) {
    return header(HEADER_ACCEPT, accept);
  }

  /**
   * Set the 'Accept' header to 'application/json'
   *
   * @return this request
   */
  public HttpRequest acceptJson() {
    return accept(CONTENT_TYPE_JSON);
  }

  /**
   * Copy from input stream to output stream
   *
   * @param input
   * @param output
   * @return this request
   * @throws IOException
   */
  protected HttpRequest copy(final InputStream input, final OutputStream output)
    throws IOException {
    return new CloseOperation<HttpRequest>(input, ignoreCloseExceptions) {

      @Override
      public HttpRequest run() throws IOException {
        final byte[] buffer = new byte[bufferSize];
        int read;
        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
          totalWritten += read;
          progress.onUpload(totalWritten, totalSize);
        }
        return HttpRequest.this;
      }
    }.call();
  }

  /**
   * Copy from reader to writer
   *
   * @param input
   * @param output
   * @return this request
   * @throws IOException
   */
  protected HttpRequest copy(final Reader input, final Writer output)
    throws IOException {
    return new CloseOperation<HttpRequest>(input, ignoreCloseExceptions) {

      @Override
      public HttpRequest run() throws IOException {
        final char[] buffer = new char[bufferSize];
        int read;
        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
          totalWritten += read;
          progress.onUpload(totalWritten, -1);
        }
        return HttpRequest.this;
      }
    }.call();
  }

  /**
   * Set the UploadProgress callback for this request
   *
   * @param callback
   * @return this request
   */
  public HttpRequest progress(final UploadProgress callback) {
    if (callback == null)
      progress = UploadProgress.DEFAULT;
    else
      progress = callback;
    return this;
  }

  private HttpRequest incrementTotalSize(final long size) {
    if (totalSize == -1)
      totalSize = 0;
    totalSize += size;
    return this;
  }

  /**
   * Close output stream
   *
   * @return this request
   * @throws IOException
   * @
   */
  protected HttpRequest closeOutput() throws IOException {
    progress(null);
    if (output == null)
      return this;
    if (multipart)
      output.write(CRLF + "--" + BOUNDARY + "--" + CRLF);
    if (ignoreCloseExceptions)
      try {
        output.close();
      } catch (IOException ignored) {
        // Ignored
      }
    else
      output.close();
    output = null;
    return this;
  }

  /**
   * Call {@link #closeOutput()} and re-throw a caught {@link IOException}s as
   * an {@link HttpRequestException}
   *
   * @return this request
   * @
   */
  protected HttpRequest closeOutputQuietly() {
    try {
      return closeOutput();
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Open output stream
   *
   * @return this request
   * @throws IOException
   */
  protected HttpRequest openOutput() throws IOException {
    if (output != null)
      return this;
    getConnection().setDoOutput(true);
    final String charset = HttpUtils.getParam(
      getConnection().getRequestProperty(HEADER_CONTENT_TYPE), PARAM_CHARSET);
    output = new RequestOutputStream(getConnection().getOutputStream(), charset,
      bufferSize);
    return this;
  }

  /**
   * Start part of a multipart
   *
   * @return this request
   * @throws IOException
   */
  protected HttpRequest startPart() throws IOException {
    if (!multipart) {
      multipart = true;
      contentType(CONTENT_TYPE_MULTIPART).openOutput();
      output.write("--" + BOUNDARY + CRLF);
    } else
      output.write(CRLF + "--" + BOUNDARY + CRLF);
    return this;
  }

  /**
   * Write part header
   *
   * @param name
   * @param filename
   * @return this request
   * @throws IOException
   */
  protected HttpRequest writePartHeader(final String name, final String filename)
    throws IOException {
    return writePartHeader(name, filename, null);
  }

  /**
   * Write part header
   *
   * @param name
   * @param filename
   * @param contentType
   * @return this request
   * @throws IOException
   */
  protected HttpRequest writePartHeader(final String name,
                                        final String filename, final String contentType) throws IOException {
    final StringBuilder partBuffer = new StringBuilder();
    partBuffer.append("form-data; name=\"").append(name);
    if (filename != null)
      partBuffer.append("\"; filename=\"").append(filename);
    partBuffer.append('"');
    partHeader("Content-Disposition", partBuffer.toString());
    if (contentType != null)
      partHeader(HEADER_CONTENT_TYPE, contentType);
    return send(CRLF);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param part
   * @return this request
   */
  public HttpRequest part(final String name, final String part) {
    return part(name, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final String part) {
    return part(name, filename, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param contentType value of the Content-Type part header
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final String contentType, final String part) {
    try {
      startPart();
      writePartHeader(name, filename, contentType);
      output.write(part);
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return this;
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final Number part) {
    return part(name, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final Number part) {
    return part(name, filename, part != null ? part.toString() : null);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final File part) {
    return part(name, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final File part) {
    return part(name, filename, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param contentType value of the Content-Type part header
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final String contentType, final File part) {
    final InputStream stream;
    try {
      stream = new BufferedInputStream(new FileInputStream(part));
      incrementTotalSize(part.length());
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return part(name, filename, contentType, stream);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final InputStream part) {
    return part(name, null, null, part);
  }

  /**
   * Write part of a multipart request to the request body
   *
   * @param name
   * @param filename
   * @param contentType value of the Content-Type part header
   * @param part
   * @return this request
   * @
   */
  public HttpRequest part(final String name, final String filename,
                          final String contentType, final InputStream part) {
    try {
      startPart();
      writePartHeader(name, filename, contentType);
      copy(part, output);
    } catch (IOException e) {
      try {
        part.close();
      } catch (IOException ignored) {
      } //when Connection timed out, close the file silently
      throw new HttpRequestException(e);
    }
    return this;
  }

  /**
   * Write a multipart header to the response body
   *
   * @param name
   * @param value
   * @return this request
   * @
   */
  @SuppressWarnings("UnusedReturnValue")
  public HttpRequest partHeader(final String name, final String value) {
    return send(name).send(": ").send(value).send(CRLF);
  }

  /**
   * Write contents of file to request body
   *
   * @param input
   * @return this request
   * @
   */
  public HttpRequest send(final File input) {
    final InputStream stream;
    try {
      stream = new BufferedInputStream(new FileInputStream(input));
      incrementTotalSize(input.length());
    } catch (FileNotFoundException e) {
      throw new HttpRequestException(e);
    }
    return send(stream);
  }

  /**
   * Write byte array to request body
   *
   * @param input
   * @return this request
   * @
   */
  public HttpRequest send(final byte[] input) {
    if (input != null) {
      incrementTotalSize(input.length);
      return send(new ByteArrayInputStream(input));
    }
    throw new IllegalArgumentException("null bytes provided");
  }

  /**
   * Write stream to request body
   * <p>
   * The given stream will be closed once sending completes
   *
   * @param input
   * @return this request
   * @
   */
  public HttpRequest send(final InputStream input) {
    try {
      openOutput();
      copy(input, output);
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return this;
  }

  /**
   * Write reader to request body
   * <p>
   * The given reader will be closed once sending completes
   *
   * @param input
   * @return this request
   * @
   */
  public HttpRequest send(final Reader input) {
    try {
      openOutput();
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    final Writer writer = new OutputStreamWriter(output,
      output.encoder.charset());
    return new FlushOperation<HttpRequest>(writer) {

      @Override
      protected HttpRequest run() throws IOException {
        return copy(input, writer);
      }
    }.call();
  }

  /**
   * Write char sequence to request body
   * <p>
   * The charset configured via {@link #contentType(String)} will be used and
   * UTF-8 will be used if it is unset.
   *
   * @param value
   * @return this request
   * @
   */
  public HttpRequest send(final CharSequence value) {
    try {
      openOutput();
      output.write(value.toString());
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return this;
  }

  /**
   * Create writer to request output stream
   *
   * @return writer
   * @
   */
  public OutputStreamWriter writer() {
    try {
      openOutput();
      return new OutputStreamWriter(output, output.encoder.charset());
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
  }

  /**
   * Write the values in the map as form data to the request body
   * <p>
   * The pairs specified will be URL-encoded in UTF-8 and sent with the
   * 'application/x-www-form-urlencoded' content-type
   *
   * @param values
   * @return this request
   * @
   */
  public HttpRequest form(final Map<?, ?> values) {
    return form(values, CHARSET_UTF8);
  }

  /**
   * Write the key and value in the entry as form data to the request body
   * <p>
   * The pair specified will be URL-encoded in UTF-8 and sent with the
   * 'application/x-www-form-urlencoded' content-type
   *
   * @param entry
   * @return this request
   * @
   */
  public HttpRequest form(final Entry<?, ?> entry) {
    return form(entry, CHARSET_UTF8);
  }

  /**
   * Write the key and value in the entry as form data to the request body
   * <p>
   * The pair specified will be URL-encoded and sent with the
   * 'application/x-www-form-urlencoded' content-type
   *
   * @param entry
   * @param charset
   * @return this request
   * @
   */
  public HttpRequest form(final Entry<?, ?> entry, final String charset) {
    return form(entry.getKey(), entry.getValue(), charset);
  }

  /**
   * Write the name/value pair as form data to the request body
   * <p>
   * The pair specified will be URL-encoded in UTF-8 and sent with the
   * 'application/x-www-form-urlencoded' content-type
   *
   * @param name
   * @param value
   * @return this request
   * @
   */
  public HttpRequest form(final Object name, final Object value) {
    return form(name, value, CHARSET_UTF8);
  }

  /**
   * Write the name/value pair as form data to the request body
   * <p>
   * The values specified will be URL-encoded and sent with the
   * 'application/x-www-form-urlencoded' content-type
   *
   * @param name
   * @param value
   * @param charset
   * @return this request
   * @
   */
  public HttpRequest form(final Object name, final Object value, String charset) {
    final boolean first = !form;
    if (first) {
      contentType(CONTENT_TYPE_FORM, charset);
      form = true;
    }
    charset = getValidCharset(charset);
    try {
      openOutput();
      if (!first)
        output.write('&');
      output.write(URLEncoder.encode(name.toString(), charset));
      output.write('=');
      if (value != null)
        output.write(URLEncoder.encode(value.toString(), charset));
    } catch (IOException e) {
      throw new HttpRequestException(e);
    }
    return this;
  }

  /**
   * Write the values in the map as encoded form data to the request body
   *
   * @param values
   * @param charset
   * @return this request
   * @
   */
  public HttpRequest form(final Map<?, ?> values, final String charset) {
    if (!values.isEmpty())
      for (Entry<?, ?> entry : values.entrySet())
        form(entry, charset);
    return this;
  }

  /**
   * Configure HTTPS connection to trust all certificates
   * <p>
   * This method does nothing if the current request is not a HTTPS request
   *
   * @return this request
   * @
   */
  public HttpRequest trustAllCerts() {
    final HttpURLConnection connection = getConnection();
    if (connection instanceof HttpsURLConnection)
      ((HttpsURLConnection) connection)
        .setSSLSocketFactory(getTrustedFactory());
    return this;
  }

  /**
   * Configure HTTPS connection to trust all hosts using a custom
   * {@link HostnameVerifier} that always returns <code>true</code> for each
   * host verified
   * <p>
   * This method does nothing if the current request is not a HTTPS request
   *
   * @return this request
   */
  public HttpRequest trustAllHosts() {
    final HttpURLConnection connection = getConnection();
    if (connection instanceof HttpsURLConnection)
      ((HttpsURLConnection) connection)
        .setHostnameVerifier(getTrustedVerifier());
    return this;
  }

  /**
   * Get the {@link URL} of this request's connection
   *
   * @return request URL
   */
  public URL url() {
    return getConnection().getURL();
  }

  /**
   * Get the HTTP method of this request
   *
   * @return method
   */
  public String method() {
    return getConnection().getRequestMethod();
  }

  /**
   * Configure an HTTP proxy on this connection. Use {{@link #proxyBasic(String, String)} if
   * this proxy requires basic authentication.
   *
   * @param proxyHost
   * @param proxyPort
   * @return this request
   */
  public HttpRequest useProxy(final String proxyHost, final int proxyPort) {
    if (connection != null)
      throw new IllegalStateException("The connection has already been created. This method must be called before reading or writing to the request.");

    this.httpProxyHost = proxyHost;
    this.httpProxyPort = proxyPort;
    return this;
  }

  /**
   * Set whether or not the underlying connection should follow redirects in
   * the response.
   *
   * @param followRedirects - true fo follow redirects, false to not.
   * @return this request
   */
  public HttpRequest followRedirects(final boolean followRedirects) {
    getConnection().setInstanceFollowRedirects(followRedirects);
    return this;
  }

  /**
   * Creates {@link HttpURLConnection HTTP connections} for
   * {@link URL urls}.
   */
  public interface ConnectionFactory {
    /**
     * A {@link ConnectionFactory} which uses the built-in
     * {@link URL#openConnection()}
     */
    ConnectionFactory DEFAULT = new ConnectionFactory() {
      public HttpURLConnection create(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
      }

      public HttpURLConnection create(URL url, Proxy proxy) throws IOException {
        return (HttpURLConnection) url.openConnection(proxy);
      }
    };

    /**
     * Open an {@link HttpURLConnection} for the specified {@link URL}.
     *
     * @throws IOException
     */
    HttpURLConnection create(URL url) throws IOException;

    /**
     * Open an {@link HttpURLConnection} for the specified {@link URL}
     * and {@link Proxy}.
     *
     * @throws IOException
     */
    HttpURLConnection create(URL url, Proxy proxy) throws IOException;
  }

  /**
   * Callback interface for reporting upload progress for a request.
   */
  public interface UploadProgress {
    UploadProgress DEFAULT = new UploadProgress() {
      public void onUpload(long uploaded, long total) {
      }
    };

    /**
     * Callback invoked as data is uploaded by the request.
     *
     * @param uploaded The number of bytes already uploaded
     * @param total    The total number of bytes that will be uploaded or -1 if
     *                 the length is unknown.
     */
    void onUpload(long uploaded, long total);
  }

  /**
   * Request output stream
   */
  public static class RequestOutputStream extends BufferedOutputStream {

    private final CharsetEncoder encoder;

    /**
     * Create request output stream
     *
     * @param stream
     * @param charset
     * @param bufferSize
     */
    public RequestOutputStream(final OutputStream stream, final String charset,
                               final int bufferSize) {
      super(stream, bufferSize);

      encoder = Charset.forName(getValidCharset(charset)).newEncoder();
    }

    /**
     * Write string to stream
     *
     * @param value
     * @return this stream
     * @throws IOException rethrows underlying io exception if any
     */
    public RequestOutputStream write(final String value) throws IOException {
      final ByteBuffer bytes = encoder.encode(CharBuffer.wrap(value));

      super.write(bytes.array(), 0, bytes.limit());

      return this;
    }
  }
}
