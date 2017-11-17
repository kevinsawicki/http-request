# Http Request 

A simple convenience library for using a [HttpURLConnection](http://download.oracle.com/javase/6/docs/api/java/net/HttpURLConnection.html)
to make requests and access the response. 

**This is a fork of the original library written by Kevin Sawicki and available [here](https://github.com/kevinsawicki/http-request) with some added bugfixes and features.** This fork is maintained by Uva Software LLC and used on [scanii.com](https://scanii.com).

This library is available under the [MIT License](http://www.opensource.org/licenses/mit-license.php).

## Usage

The http-request library is available from Maven central:

```xml
<dependency>
  <groupId>com.uvasoftware.http</groupId>
  <artifactId>http-request</artifactId>
  <version>${latest-version}</version>
</dependency>
```

## FAQ

### Why was this written?

This library was written to make HTTP requests simple and easy when using a `HttpURLConnection`.

Libraries like [Apache HttpComponents](http://hc.apache.org) are great but sometimes
for either simplicity, or perhaps for the environment you are deploying to (Android),
you just want to use a good old-fashioned `HttpURLConnection`.  This library seeks
to add convenience and common patterns to the act of making HTTP requests such as
a fluid-interface for building requests and support for features such as multipart
requests.

**Bottom line:** The single goal of this library is to improve the usability of the
`HttpURLConnection` class.

### What are the dependencies?

**None**.  The goal of this library is to be a single class class with some inner static
classes.  The test project does require [Jetty](http://eclipse.org/jetty/) in order
to test requests against an actual HTTP server implementation.

### How are exceptions managed?

The `HttpRequest` class does not throw any checked exceptions, instead all low-level
exceptions are wrapped up in a `HttpRequestException` which extends `RuntimeException`.
You can access the underlying exception by catching `HttpRequestException` and calling
`getCause()` which will always return the original `IOException`.

### Are requests asynchronous?

**No**.  The underlying `HttpUrlConnection` object that each `HttpRequest`
object wraps has a synchronous API and therefore all methods on `HttpRequest`
are also synchronous.

Therefore it is important to not use an `HttpRequest` object on the main thread
of your application.

Here is a simple Android example of using it from an
[AsyncTask](http://developer.android.com/reference/android/os/AsyncTask.html):

```java
private class DownloadTask extends AsyncTask<String, Long, File> {
  protected File doInBackground(String... urls) {
    try {
      HttpRequest request =  HttpRequest.get(urls[0]);
      File file = null;
      if (request.ok()) {
        file = File.createTempFile("download", ".tmp");
        request.receive(file);
        publishProgress(file.length());
      }
      return file;
    } catch (HttpRequestException exception) {
      return null;
    }
  }

  protected void onProgressUpdate(Long... progress) {
    Log.d("MyApp", "Downloaded bytes: " + progress[0]);
  }

  protected void onPostExecute(File file) {
    if (file != null)
      Log.d("MyApp", "Downloaded file to: " + file.getAbsolutePath());
    else
      Log.d("MyApp", "Download failed");
  }
}

new DownloadTask().execute("http://google.com");
```

## Examples

### Perform a GET request and get the status of the response

```java
int response = HttpRequest.get("http://google.com").code();
```

### Perform a GET request and get the body of the response

```java
String response = HttpRequest.get("http://google.com").body();
System.out.println("Response was: " + response);
```

### Print the response of a GET request to standard out

```java
HttpRequest.get("http://google.com").receive(System.out);
```

### Adding query parameters

```java
HttpRequest request = HttpRequest.get("http://google.com", true, 'q', "baseball gloves", "size", 100);
System.out.println(request.toString()); // GET http://google.com?q=baseball%20gloves&size=100
```

### Using arrays as query parameters

```java
int[] ids = new int[] { 22, 23 };
HttpRequest request = HttpRequest.get("http://google.com", true, "id", ids);
System.out.println(request.toString()); // GET http://google.com?id[]=22&id[]=23
```

### Working with request/response headers

```java
String contentType = HttpRequest.get("http://google.com")
                                .accept("application/json") //Sets request header
                                .contentType(); //Gets response header
System.out.println("Response content type was " + contentType);
```

### Perform a POST request with some data and get the status of the response

```java
int response = HttpRequest.post("http://google.com").send("name=kevin").code();
```

### Authenticate using Basic authentication

```java
int response = HttpRequest.get("http://google.com").basic("username", "p4ssw0rd").code();
```

### Perform a multipart POST request

```java
HttpRequest request = HttpRequest.post("http://google.com");
request.part("status[body]", "Making a multipart request");
request.part("status[image]", new File("/home/kevin/Pictures/ide.png"));
if (request.ok())
  System.out.println("Status was updated");
```

### Perform a POST request with form data

```java
Map<String, String> data = new HashMap<String, String>();
data.put("user", "A User");
data.put("state", "CA");
if (HttpRequest.post("http://google.com").form(data).created())
  System.out.println("User was created");
```

### Copy body of response to a file

```java
File output = new File("/output/request.out");
HttpRequest.get("http://google.com").receive(output);
```
### Post contents of a file

```java
File input = new File("/input/data.txt");
int response = HttpRequest.post("http://google.com").send(input).code();
```

### Using entity tags for caching

```java
File latest = new File("/data/cache.json");
HttpRequest request = HttpRequest.get("http://google.com");
//Copy response to file
request.receive(latest);
//Store eTag of response
String eTag = request.eTag();
//Later on check if changes exist
boolean unchanged = HttpRequest.get("http://google.com")
                               .ifNoneMatch(eTag)
                               .notModified();
```

### Using gzip compression

```java
HttpRequest request = HttpRequest.get("http://google.com");
//Tell server to gzip response and automatically uncompress
request.acceptGzipEncoding().uncompress(true);
String uncompressed = request.body();
System.out.println("Uncompressed response is: " + uncompressed);
```

### Ignoring security when using HTTPS

```java
HttpRequest request = HttpRequest.get("https://google.com");
//Accept all certificates
request.trustAllCerts();
//Accept all hostnames
request.trustAllHosts();
```

### Configuring an HTTP proxy

```java
HttpRequest request = HttpRequest.get("https://google.com");
//Configure proxy
request.useProxy("localhost", 8080);
//Optional proxy basic authentication
request.proxyBasic("username", "p4ssw0rd");
```

### Following redirects

```java
int code = HttpRequest.get("http://google.com").followRedirects(true).code();
```

### Custom connection factory

Looking to use this library with [OkHttp](https://github.com/square/okhttp)?
Read [here](https://gist.github.com/JakeWharton/5797571).

```java
HttpRequest.setConnectionFactory(new ConnectionFactory() {

  public HttpURLConnection create(URL url) throws IOException {
    if (!"https".equals(url.getProtocol()))
      throw new IOException("Only secure requests are allowed");
    return (HttpURLConnection) url.openConnection();
  }

  public HttpURLConnection create(URL url, Proxy proxy) throws IOException {
    if (!"https".equals(url.getProtocol()))
      throw new IOException("Only secure requests are allowed");
    return (HttpURLConnection) url.openConnection(proxy);
  }
});
```
### Posting json content

This will post the JSON content as well as setup the proper content type.

```java
HttpRequest r = HttpRquest.post("https://httpbin.org/post").json("{\"name\":\"user\",\"number\":\"1001\"}");

if (r.ok())
  System.out.println("Status was updated");
```
