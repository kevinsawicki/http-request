package com.github.kevinsawicki.http;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Levi Notik
 * Date: 9/8/12
 */
public class RequestParams {

        private ConcurrentHashMap<String, String> params;

        public RequestParams(String key, String value) {
            this();
            params.put(key, value);
        }

        private RequestParams(Builder builder) {
            this();
            params.putAll(builder.params);
        }

        public RequestParams() {
            params = new ConcurrentHashMap<String, String>();
        }

        public void put(String key, String value) {
            params.putIfAbsent(key, value);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            for(ConcurrentHashMap.Entry<String, String> entry : params.entrySet()) {
                if(result.length() > 0)
                    result.append("&");

                result.append(entry.getKey());
                result.append("=");
                result.append(entry.getValue());
            }

            return result.toString();
        }

    public static class Builder {

        private HashMap<String, String> params = new HashMap<String, String>();

        public Builder param(String key, String value) {
            params.put(key, value);
            return this;
        }

        public RequestParams build() {
            return new RequestParams(this);
        }
    }
}
