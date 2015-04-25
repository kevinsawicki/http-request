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

/*
 * TLSSocketFactory added by Fmstrat is used to enable TLSv1.1 and TLSv1.2 support
 * Supported protocols per API can be found at:
 *       https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
 */
package com.github.kevinsawicki.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class TLSSocketFactory extends SSLSocketFactory {

	private final javax.net.ssl.SSLSocketFactory socketFactory;

	public TLSSocketFactory(SSLContext sslContext) {
		super();
		this.socketFactory = sslContext.getSocketFactory();
   	}

	@Override
	public Socket createSocket(
		final Socket socket,
		final String host,
		final int port,
		final boolean autoClose
	) throws java.io.IOException {
		SSLSocket sslSocket = (SSLSocket) this.socketFactory.createSocket(
			socket,
			host,
			port,
			autoClose
		);
		sslSocket.setEnabledProtocols(sslSocket.getSupportedProtocols());
		return sslSocket;
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return this.socketFactory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return this.socketFactory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(String s, int i) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(String s, int i, InetAddress inetAddress, int i2) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress2, int i2) throws IOException {
		return null;
	}
}
