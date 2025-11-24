// Tool calling client library
//> using dep "com.lihaoyi::upickle:4.4.1"

import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader, OutputStream}
import upickle.default.*

case class ToolResponse(
  result: String,
  success: Boolean,
  error: Option[String] = None
) derives ReadWriter

def callTool(toolName: String, arguments: String): String = {
  val port = sys.env.getOrElse("TOOL_SERVER_PORT", "8080").toInt
  val url = new URL(s"http://localhost:$port/")
  val connection = url.openConnection().asInstanceOf[HttpURLConnection]

  try {
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", "application/json")

    val requestBody = s"""{"toolName":"$toolName","arguments":"${arguments.replace("\"", "\\\"")}"}"""

    val os: OutputStream = connection.getOutputStream
    os.write(requestBody.getBytes("UTF-8"))
    os.close()

    val responseCode = connection.getResponseCode
    if (responseCode == 200) {
      val in = new BufferedReader(new InputStreamReader(connection.getInputStream))
      val response = new StringBuilder
      var line = in.readLine()
      while (line != null) {
        response.append(line)
        line = in.readLine()
      }
      in.close()

      val toolResponse = read[ToolResponse](response.toString)
      if (toolResponse.success) {
        toolResponse.result
      } else {
        val errorMsg = toolResponse.error.getOrElse("Unknown error")
        println(s"Error calling tool '$toolName': $errorMsg")
        sys.exit(1)
      }
    } else {
      println(s"Error: HTTP $responseCode - Failed to call tool '$toolName'")
      sys.exit(1)
    }
  } finally {
    connection.disconnect()
  }
}
