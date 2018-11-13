# reactive-gremlin
High throughput akka http gremlin 3 websocket connector with backpressure.

High Flow Gremlin ....  Blows your hair back !

![gremlin](https://cdn.obsev.com/wp-content/uploads/2018/04/Gremlins-Gizmo-1024x688.jpg.webp "Blow your hair back!")

### What is reactive-gremlin
reactive-gremlin is a `streaming` websocket client that connects to a gremlin server to execute gremlin scripts.  Current client including the Java client provided by datastax have the capability to crash your gremlin server by overflowing the server with asyn requests.  Your gremlin server will buffer incoming requests and service them when it has finished the ones in the queue.  For fast consumers (bulk loaders) it is possible to cause Out of memory Exceptions and crash the server.   

reactive-gremlin provides a way around this by implementing backpressure through a side channel.  This will slow down the fast consumer while the server is overloaded and speed it up when the server catches up.  It does this throw a user controlled parameter on the maximum `in-flight` calls allowed to any one server.  The stream monitors responses from the server and controlls backpressure on the stream accordingly.

### Build.sbt
Add the following dependency to your porject.

`resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"`

`"io.surfkit" %% "reactive-gremlin" % "0.0.1"`


## Usage

### scripts
I have provided a simple builder to convert you gremlin scripts into the required types for transport.  Here are some examples of encoding a few gremlin `groovy` scripts

```scala
val simple = GremlinClient.buildRequest("g.V().has('email','donald@trumpdonald.org').has('is_douchebag','true').valueMap();")
val create = GremlinClient.buildRequest("graph.addVertex(label, 'entity','uri','https://en.wikipedia.org/wiki/Donald_Trump');")
```
### client
To issue request to your server you must create a `GremlinClient`.  Parameters:
* `host: String` is the websocket url to connect to your server (default localhost:8182).
* `maxInFlight:Int` controls the max number of request that you will allow you server to process at one time (default 250)
* `responder:Option[ActorRef]` an optional Akka actor that will receive `Gremlin.Response` messages.
* `onResponse:Option[Gremline.Response => Unit]` an optional callback that will receive `Gremlin.Response` messages.
```scala
val gclient = new GremlinClient(host="ws://localhost:8182",maxInFlight=100)
```

### pushing data
There are 3 ways to push your request data into the flow:
* as a driver that will return a `Future`
* via `ActorRef` publisher (usefull for standard interaction with your gremlin server)
* via `Flow[GremlinRequest, _]` flow that you create from another `Source`.  This is extreamly usfull when bulk loading data from a file or other source.  More on this below.

### driver returning a future example
```scala
val gclient = new GremlinClient(host="ws://localhost:8182",maxInFlight=100)
gclient.query("g.V().has('email','donald@trumpdonald.org').has('is_douchebag','true').valueMap();")
       .map{ x: Gremlin.Response =>
          val json: Option[List[JsValue]] = x.result.data
          ...
       }
```

### actor push example
Here is a simple but full example of how to use the client
```scala
def response(res:Gremlin.Response):Unit = {
  println(s"The response is ${res}")
}

val gclient = new GremlinClient(host="ws://localhost:8182",maxInFlight=100, onResponse = Some(response))
val producer = gclient.connectActor

val simple = GremlinClient.buildRequest("g.V().has('email','donald@trumpdonald.org').has('is_douchebag','true').valueMap();")
val create = GremlinClient.buildRequest("graph.addVertex(label, 'entity','uri','https://en.wikipedia.org/wiki/Donald_Trump');")

producer ! simple
producer ! create

````

### flow example
Here is a file streaming example (bulk loader)
```scala
def response(res:Gremlin.Response):Unit = {
  println(s"The response is ${res}")
}

val gclient = new GremlinClient(host="ws://localhost:8182",maxInFlight=100, onResponse = Some(response))

val csv = new File("/path/to/csv")
val flow = FileIO.fromFile(outFile)
          .via(Framing.delimiter(
            ByteString("\n"),
            maximumFrameLength = 1000000,
            allowTruncation = false))
          .map(_.utf8String)
          .mapConcat{ line =>
            // parse and form some gremlin groove insert script
            // we call that `gscript`
            GremlinClient.buildRequest(gscript)
          }
          
gclient.connectFlow(flow)   // connect and run graph

````
