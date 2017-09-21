package com.uvasoftware.http.internal;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

class VerifyHolder {
  private static final HostnameVerifier TRUSTED_VERIFIER = new HostnameVerifier() {

    public boolean verify(String hostname, SSLSession session) {
      return true;
    }
  };
}
