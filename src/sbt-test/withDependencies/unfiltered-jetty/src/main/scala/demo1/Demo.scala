package demo1

object Demo {
  def main(args: Array[String]): Unit = {
    val server = unfiltered.jetty.Server.http(unfiltered.util.Port.any)
    server.start()
    println("Hello World")
    server.stop()
  }
}
