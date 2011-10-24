# Http Request

A simple convenience library for using a [HttpURLConnection](http://download.oracle.com/javase/6/docs/api/java/net/HttpURLConnection.html)
to make requests and access the response. 

## FAQ

### Why was this written?

This library was written to make HTTP requests simple and easy when using a `HttpURLConnection`.

Libraries like [Apache HttpComponents](http://hc.apache.org) are great but sometimes
for either simplicity, or perhaps for the environment you are deploying to (Android),
you just want to use a good old-fashioned `HttpURLConnection`.  This library seeks
to add convenience and common patterns to the act of making HTTP requests such as
a fluid-interface for building requests and support for features such as multipart
requests.

*Bottom line:* The single goal of this library is to improve the usability of the
`HttpURLConnection` class.

### What are the dependencies?

None.  The goal of this library is to be a single class class with some inner static
classes.  The test project does require [Jetty](http://eclipse.org/jetty/) in order
to test requests against an actual HTTP server implementation.

### How are exceptions managed?

The `HttpRequest` class does not throw any checked exceptions, instead all low-level
exceptions are wrapped up in a `RequestException` which extends `RuntimeException`.
You can access the underlying exception by catching `RequestException` and calling
`getCause()`.


## Examples
Perform a GET request and get the status of the response

```java
int response = HttpRequest.get("http://google.com").code();
```

Perform a GET request and get the body of the response

```java
String response = HttpRequest.get("http://google.com").string();
System.out.println("Response was: " + response);
```

Perform a POST request with some data and get the status of the response

```java
int response = HttpRequest.post("http://google.com").body("name=kevin").code();
```

Authenticate using Basic authentication

```java
int response = HttpRequest.get("http://google.com").basic("username", "p4ssw0rd").code();
```

Perform a multipart POST request

```java
HttpRequest request = HttpRequest.post("http://google.com");
request.part("status[body]", "Making a multipart request");
request.part("status[image]", new File("/home/kevin/Pictures/ide.png"));
if (200 = request.code())
  System.out.println("Status was updated");
```

Perform a POST request with form data

```java
Map<String, String> data = new HashMap<String, String>();
data.put("user", "A User");
data.put("state", "CA");
int code = HttpRequest.post("http://google.com").form(data).code();
```
## Contributors

* [Kevin Sawicki](https://github.com/kevinsawicki) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=kevinsawicki)
* [Eddie Ringle](https://github.com/eddieringle) :: [contributions](https://github.com/kevinsawicki/http-request/commits?author=eddieringle)
