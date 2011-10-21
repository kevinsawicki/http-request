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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A fluid interface for making HTTP requests using an underlying
 * {@link HttpURLConnection} (or sub-class).
 */
public class HttpRequest {

	/**
	 * 'GET' request method
	 */
	public static final String METHOD_GET = "GET";

	/**
	 * 'HEAD' request method
	 */
	public static final String METHOD_HEAD = "HEAD";

	/**
	 * 'DELETE' request method
	 */
	public static final String METHOD_DELETE = "DELETE";

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

	private static final String BOUNDARY = "----1010101010";

	/**
	 * Request exception
	 */
	public static class RequestException extends RuntimeException {

		/** serialVersionUID */
		private static final long serialVersionUID = -1170466989781746231L;

		/**
		 * @param message
		 * @param cause
		 */
		public RequestException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * @param message
		 */
		public RequestException(String message) {
			super(message);
		}

		/**
		 * @param cause
		 */
		public RequestException(Throwable cause) {
			super(cause);
		}
	}

	/**
	 * Start a 'GET' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest get(String url) throws RequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * Start a 'GET' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest get(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * Start a 'POST' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest post(String url) throws RequestException {
		return new HttpRequest(url, METHOD_POST);
	}

	/**
	 * Start a 'POST' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest post(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_POST);
	}

	/**
	 * Start a 'PUT' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest put(String url) throws RequestException {
		return new HttpRequest(url, METHOD_PUT);
	}

	/**
	 * Start a 'PUT' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest put(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_PUT);
	}

	/**
	 * Start a 'DELETE' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest delete(String url) throws RequestException {
		return new HttpRequest(url, METHOD_DELETE);
	}

	/**
	 * Start a 'DELETE' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest delete(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_DELETE);
	}

	/**
	 * Start a 'HEAD' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest head(String url) throws RequestException {
		return new HttpRequest(url, METHOD_HEAD);
	}

	/**
	 * Start a 'HEAD' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest head(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_HEAD);
	}

	/**
	 * Start a 'OPTIONS' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest options(String url) throws RequestException {
		return new HttpRequest(url, METHOD_OPTIONS);
	}

	/**
	 * Start a 'OPTIONS' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest options(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_OPTIONS);
	}

	/**
	 * Start a 'TRACE' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest trace(String url) throws RequestException {
		return new HttpRequest(url, METHOD_TRACE);
	}

	/**
	 * Start a 'TRACE' request to the given URL
	 *
	 * @param url
	 * @return request
	 * @throws RequestException
	 */
	public static HttpRequest trace(URL url) throws RequestException {
		return new HttpRequest(url, METHOD_TRACE);
	}

	private final HttpURLConnection connection;

	private DataOutputStream output;

	private int bufferSize = 8196;

	/**
	 * Create HTTP connection wrapper
	 *
	 * @param url
	 * @param method
	 * @throws RequestException
	 */
	public HttpRequest(String url, String method) throws RequestException {
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod(method);
		} catch (IOException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Create HTTP connection wrapper
	 *
	 * @param url
	 * @param method
	 * @throws RequestException
	 */
	public HttpRequest(URL url, String method) throws RequestException {
		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);
		} catch (IOException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Get underlying connection
	 *
	 * @return connection
	 */
	public HttpURLConnection getConnection() {
		return connection;
	}

	/**
	 * Get the status code of the response
	 *
	 * @return this request
	 * @throws RequestException
	 */
	public int code() throws RequestException {
		closeOutput();
		try {
			return connection.getResponseCode();
		} catch (IOException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Get status message of the response
	 *
	 * @return message
	 */
	public String message() {
		closeOutput();
		try {
			return connection.getResponseMessage();
		} catch (IOException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Disconnect the connection
	 *
	 * @return this request
	 */
	public HttpRequest disconnect() {
		connection.disconnect();
		return this;
	}

	/**
	 * Set the buffer size used when copying between streams
	 *
	 * @param size
	 * @return this request
	 */
	public HttpRequest bufferSize(int size) {
		if (size < 1)
			throw new IllegalArgumentException("Size must be greater than zero");
		bufferSize = size;
		return this;
	}

	/**
	 * Get response as String
	 *
	 * @return string
	 * @throws RequestException
	 */
	public String string() throws RequestException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream(
				contentLength());
		copy(buffer(), output);
		final String charset = charset();
		if (charset == null)
			return output.toString();
		try {
			return output.toString(charset);
		} catch (UnsupportedEncodingException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Get response as byte array
	 *
	 * @return byte array
	 * @throws RequestException
	 */
	public byte[] bytes() throws RequestException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream(
				contentLength());
		copy(buffer(), output);
		return output.toByteArray();
	}

	/**
	 * Get response in a buffered stream
	 *
	 * @return stream
	 * @throws RequestException
	 */
	public BufferedInputStream buffer() throws RequestException {
		return new BufferedInputStream(stream());
	}

	/**
	 * Get stream to response
	 *
	 * @return stream
	 * @throws RequestException
	 */
	public InputStream stream() throws RequestException {
		try {
			return connection.getInputStream();
		} catch (IOException e) {
			throw new RequestException(e);
		}
	}

	/**
	 * Set read timeout on connection to value
	 *
	 * @param timeout
	 * @return this request
	 */
	public HttpRequest readTimeout(int timeout) {
		connection.setReadTimeout(timeout);
		return this;
	}

	/**
	 * Set header name to given value
	 *
	 * @param name
	 * @param value
	 * @return this request
	 */
	public HttpRequest header(String name, String value) {
		connection.setRequestProperty(name, value);
		return this;
	}

	/**
	 * Get a response header
	 *
	 * @param name
	 * @return response header
	 */
	public String header(String name) {
		return connection.getHeaderField(name);
	}

	/**
	 * Get 'charset' parameter from 'Content-Type' response header
	 *
	 * @return charset or null if none
	 */
	public String charset() {
		String contentType = contentType();
		if (contentType == null || contentType.length() == 0)
			return null;
		int postSemi = contentType.indexOf(';') + 1;
		if (postSemi > 0 && postSemi == contentType.length())
			return null;
		String[] params = contentType.substring(postSemi).split(";");
		final String charsetParam = "charset";
		for (String param : params) {
			String[] split = param.split("=");
			if (split.length != 2)
				continue;
			if (!charsetParam.equals(split[0]))
				continue;

			String charset = split[1];
			int length = charset.length();
			if (length == 0)
				continue;
			if (length > 2 && '"' == charset.charAt(0)
					&& '"' == charset.charAt(length - 1))
				charset = charset.substring(1, length - 1);
			return charset;
		}
		return null;
	}

	/**
	 * Set the 'User-Agent' header to given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest userAgent(String value) {
		return header("User-Agent", value);
	}

	/**
	 * Set the 'Host' header to given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest host(String value) {
		return header("Host", value);
	}

	/**
	 * Set the 'Accept-Encoding' header to given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest acceptEncoding(String value) {
		return header("Accept-Encoding", value);
	}

	/**
	 * Get the 'Content-Encoding' header from the response
	 *
	 * @return this request
	 */
	public String contentEncoding() {
		return header("Content-Encoding");
	}

	/**
	 * Get the 'Server' header from the response
	 *
	 * @return server
	 */
	public String server() {
		return header("Server");
	}

	/**
	 * Get the 'Date' header from the response
	 *
	 * @return date value, -1 on failures
	 */
	public long date() {
		return connection.getHeaderFieldDate("Date", -1L);
	}

	/**
	 * Get the 'Expires' header from the response
	 *
	 * @return expires value, -1 on failures
	 */
	public long expires() {
		return connection.getHeaderFieldDate("Expires", -1L);
	}

	/**
	 * Set the 'Authentication' header to given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest authentication(final String value) {
		return header("Authentication", value);
	}

	/**
	 * Set the 'Content-Type' request header to the given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest contentType(final String value) {
		return header("Content-Type", value);
	}

	/**
	 * Get the 'Content-Type' header from the response
	 *
	 * @return response header value
	 */
	public String contentType() {
		return header("Content-Type");
	}

	/**
	 * Get the 'Content-Type' header from the response
	 *
	 * @return response header value
	 */
	public int contentLength() {
		return connection.getHeaderFieldInt("Content-Length", -1);
	}

	/**
	 * Set the 'Content-Length' request header to the given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest contentLength(final String value) {
		return header("Content-Length", value);
	}

	/**
	 * Set the 'Accept' header to given value
	 *
	 * @param value
	 * @return this request
	 */
	public HttpRequest accept(final String value) {
		return header("Accept", value);
	}

	/**
	 * Copy between streams
	 *
	 * @param input
	 * @param output
	 * @return this request
	 * @throws RequestException
	 */
	protected HttpRequest copy(final InputStream input,
			final OutputStream output) throws RequestException {
		final byte[] buffer = new byte[bufferSize];
		int read;
		try {
			while ((read = input.read(buffer)) != -1)
				output.write(buffer, 0, read);
		} catch (IOException e) {
			throw new RequestException(e);
		} finally {
			try {
				input.close();
			} catch (IOException e) {
				throw new RequestException(e);
			}
		}
		return this;
	}

	/**
	 * Close output stream
	 *
	 * @return this request
	 * @throws RequestException
	 */
	protected HttpRequest closeOutput() throws RequestException {
		if (output == null)
			return this;
		try {
			output.writeBytes("\r\n--");
			output.writeBytes(BOUNDARY);
			output.writeBytes("--\r\n");
			output.close();
			output = null;
		} catch (IOException e) {
			throw new RequestException(e);
		}
		return this;
	}

	/**
	 * Write part to stream
	 *
	 * @param name
	 * @param value
	 * @return this request
	 * @throws RequestException
	 */
	public HttpRequest part(final String name, final Object value)
			throws RequestException {
		try {
			if (output == null) {
				output = new DataOutputStream(connection.getOutputStream());
				contentType("multipart/form-data; boundary=" + BOUNDARY);
				output.writeBytes("--");
				output.writeBytes(BOUNDARY);
				output.writeBytes("\r\n");
			} else
				output.writeBytes("\r\n--" + BOUNDARY + "\r\n");

			output.writeBytes("Content-Disposition: form-data; name=\"");
			output.writeBytes(name);
			output.writeBytes("\"\r\n\r\n");
			if (value instanceof InputStream)
				copy((InputStream) value, output);
			else if (value instanceof File)
				copy(new FileInputStream((File) value), output);
			else
				output.writeBytes(value.toString());
		} catch (IOException e) {
			throw new RequestException(e);
		}
		return this;
	}

	/**
	 * Write stream to request body
	 *
	 * @param input
	 * @return this request
	 * @throws RequestException
	 */
	public HttpRequest body(final InputStream input) throws RequestException {
		connection.setDoOutput(true);
		try {
			copy(input, connection.getOutputStream());
		} catch (IOException e) {
			throw new RequestException(e);
		}
		return this;
	}

	/**
	 * Write string as UTF-8 bytes to request body
	 *
	 * @param value
	 * @return this request
	 * @throws RequestException
	 */
	public HttpRequest body(final String value) throws RequestException {
		return body(value, "UTF-8");
	}

	/**
	 * Write string in given charset to request body.
	 * <p>
	 * The platform's default charset will be used if charset is null
	 *
	 * @param value
	 * @param charset
	 * @return this request
	 * @throws RequestException
	 */
	public HttpRequest body(final String value, final String charset)
			throws RequestException {
		connection.setDoOutput(true);
		final byte[] bytes;
		if (charset != null)
			try {
				bytes = value.getBytes(charset);
			} catch (UnsupportedEncodingException e) {
				throw new RequestException(e);
			}
		else
			bytes = value.getBytes();
		return body(new ByteArrayInputStream(bytes));
	}

	/**
	 * Configure HTTPS connection to trust all certificates
	 *
	 * @return this request
	 * @throws RequestException
	 */
	public HttpRequest trustAllCerts() throws RequestException {
		if (!(connection instanceof HttpsURLConnection))
			return this;
		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };
		final SSLContext context;
		try {
			context = SSLContext.getInstance("TLS");
			context.init(null, trustAllCerts, new SecureRandom());
		} catch (KeyManagementException e) {
			throw new RequestException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RequestException(e);
		}
		((HttpsURLConnection) connection).setSSLSocketFactory(context
				.getSocketFactory());
		return this;
	}

	/**
	 * Configured HTTPS connection to trust all hosts
	 *
	 * @return this request
	 */
	public HttpRequest trustAllHosts() {
		if (!(connection instanceof HttpsURLConnection))
			return this;
		((HttpsURLConnection) connection)
				.setHostnameVerifier(new HostnameVerifier() {

					public boolean verify(String hostname, SSLSession session) {
						return true;
					}
				});
		return this;
	}
}
