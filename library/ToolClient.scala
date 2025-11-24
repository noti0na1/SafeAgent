// Tool calling client library
//> using dep "com.lihaoyi::upickle:4.4.1"
//> using dep "com.lihaoyi::requests:0.9.0"

def callTool(toolName: String, arguments: String): String = {
  val port = sys.env.getOrElse("TOOL_SERVER_PORT", "8080").toInt
  val url = s"http://localhost:$port/"

  val requestBody = ujson.Obj(
    "toolName" -> toolName,
    "arguments" -> arguments
  )

  try {
    val response = requests.post(
      url,
      data = requestBody.toString(),
      headers = Map("Content-Type" -> "application/json")
    )

    if (response.statusCode == 200) {
      val json = ujson.read(response.text())

      if (json("success").bool) {
        json("result").str
      } else {
        val errorMsg = json.obj.get("error").map(_.str).getOrElse("Unknown error")
        println(s"Error calling tool '$toolName': $errorMsg")
        sys.exit(1)
      }
    } else {
      println(s"Error: HTTP ${response.statusCode} - Failed to call tool '$toolName'")
      sys.exit(1)
    }
  } catch {
    case e: requests.RequestFailedException =>
      println(s"Error calling tool '$toolName': ${e.getMessage}")
      sys.exit(1)
    case e: Exception =>
      println(s"Unexpected error calling tool '$toolName': ${e.getMessage}")
      sys.exit(1)
  }
}
