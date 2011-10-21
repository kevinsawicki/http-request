# Http-Request

A simple convenience library for using a [HttpURLConnection](http://download.oracle.com/javase/6/docs/api/java/net/HttpURLConnection.html)

## Examples
Perform a GET request and get the status of the response

```java
int response = HttpRequest.get("http://google.com").code();
```

Perform a POST request with some data and get the status of the response

```java
int response = HttpRequest.post("http://google.com").body("name=kevin").code();
```
