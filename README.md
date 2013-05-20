# Http Request [![Build Status](https://travis-ci.org/kevinsawicki/http-request.png)](https://travis-ci.org/kevinsawicki/http-request)

A simple convenience library for using a [HttpURLConnection](http://download.oracle.com/javase/6/docs/api/java/net/HttpURLConnection.html)
to make requests and access the response.

## Usage

The http-request library is available from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.github.kevinsawicki%22%20AND%20a%3A%22http-request%22).

```xml
<dependency>
  <groupId>com.github.kevinsawicki</groupId>
  <artifactId>http-request</artifactId>
  <version>4.2</version>
</dependency>
```

Javadocs are available [here](http://kevinsawicki.github.com/http-request/apidocs/index.html).

## FAQ

### Who uses this?

See [here](https://github.com/kevinsawicki/http-request/wiki/Used-By) for a
list of known projects using this library.

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

None.  The goal of this library is to be a single class class with some inner static
classes.  The test project does require [Jetty](http://eclipse.org/jetty/) in order
to test requests against an actual HTTP server implementation.

### How are exceptions managed?

The `HttpRequest` class does not throw any checked exceptions, instead all low-level
exceptions are wrapped up in a `HttpRequestException` which extends `RuntimeException`.
You can access the underlying exception by catching `HttpRequestException` and calling
`getCause()` which will always return the original `IOException`.

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

### Configure HTTP proxy

```java
HttpRequest request = HttpRequest.get("https://google.com");
//Configure proxy
request.useProxy("localhost", 8080);
//Optional proxy basic uthentication
request.proxyBasic("username", "p4ssw0rd");
```

## Contributors

* [Kevin Sawicki](https://github.com/kevinsawicki) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=kevinsawicki)
* [Eddie Ringle](https://github.com/eddieringle) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=eddieringle)
* [Sean Jensen-Grey](https://github.com/seanjensengrey) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=seanjensengrey)
* [Levi Notik](https://github.com/levinotik) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=levinotik)
* [Michael Wang](https://github.com/michael-wang) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=michael-wang)
* [Julien HENRY](https://github.com/henryju) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=henryju)
