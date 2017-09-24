package com.uvasoftware.http;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

class TestUtils {
  private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

  static void setupLogging() {
    Logger log = Logger.getLogger("com.uvasoftware.http");
    log.setLevel(Level.ALL);
    log.setUseParentHandlers(false);
    ConsoleHandler handler = new ConsoleHandler();
    handler.setLevel(Level.FINE);

    handler.setFormatter(new Formatter() {
      @Override
      public String format(LogRecord record) {
        return String.format("%s [%s] %s: %s\n", new SimpleDateFormat(PATTERN)
          .format(new Date(record.getMillis())), record.getLoggerName(), record.getLevel().getName(), formatMessage(record));
      }
    });

    log.addHandler(handler);
  }
}
