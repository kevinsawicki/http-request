# Http-Request

A simple convenience library for using a [HttpURLConnection](http://download.oracle.com/javase/6/docs/api/java/net/HttpURLConnection.html)
to make requests and access the response.

## Examples
Perform a GET request and get the status of the response

```java
int response = HttpRequest.get("http://google.com").code();
```

Perform a POST request with some data and get the status of the response

```java
int response = HttpRequest.post("http://google.com").body("name=kevin").code();
```

Perform a multipart POST request

```java
HttpRequest request = HttpRequest.post("http://google.com");
request.part("status[body]", "Making a multipart request");
request.part("status[image]", new FileInputStream("/home/kevin/Pictures/ide.png"));
if (200 = requeste.code())
  System.out.println("Status was updated");
