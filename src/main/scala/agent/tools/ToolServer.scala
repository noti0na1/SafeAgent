package agent.tools

import agent.core.{ToolBase, State}
import upickle.default.*
import scala.util.{Try, Success, Failure}
import java.net.{ServerSocket, Socket}
import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import scala.concurrent.Promise

/**
 * Request format for tool execution over HTTP
 *
 * @param toolName Name of the tool to execute
 * @param arguments JSON string of tool arguments
 */
case class ToolRequest(
  toolName: String,
  arguments: String
) derives ReadWriter

/**
 * Response format for tool execution over HTTP
 *
 * @param result The JSON result from the tool
 * @param success Whether the execution succeeded
 * @param error Optional error message
 */
case class ToolResponse(
  result: String,
  success: Boolean,
  error: Option[String] = None
) derives ReadWriter

/**
 *  HTTP server that exposes agent tools for inter-process communication.
 *
 *  This server allows code running in external processes (like the scala-repl
 *  in EvalTool) to call back into the agent's available tools.
 */
class ToolServer(tools: List[ToolBase], port: Int = 8080)(using state: State) extends AutoCloseable:
  private var serverSocket: Option[ServerSocket] = None
  private var serverThread: Option[Thread] = None

  /** Get the actual port the server is listening on */
  def getPort: Option[Int] = serverSocket.map(_.getLocalPort)

  /** Start the server in a background thread */
  def start(): Unit =
    val socket = new ServerSocket(port)
    serverSocket = Some(socket)
    val thread = new Thread(() => {
      while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
        try {
          val clientSocket = socket.accept()
          handleClient(clientSocket)
        } catch {
          case _: java.net.SocketException if socket.isClosed =>
            // Server was closed, exit gracefully
          case ex: Exception =>
            System.err.println(s"Error handling client: ${ex.getMessage}")
        }
      }
    })
    thread.setDaemon(true)
    thread.start()
    serverThread = Some(thread)

  /** Handle a client connection */
  private def handleClient(clientSocket: Socket): Unit =
    try {
      val in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
      val out = new PrintWriter(clientSocket.getOutputStream, true)

      // Read HTTP request
      val requestLine = in.readLine()
      if (requestLine != null && requestLine.startsWith("POST")) {
        // Read headers until empty line
        var contentLength = 0
        var line: String | Null = in.readLine()
        while (line != null && line.nonEmpty) {
          if (line.toLowerCase.startsWith("content-length:")) {
            contentLength = line.split(":")(1).trim.toInt
          }
          line = in.readLine()
        }

        // Read body
        val bodyChars = new Array[Char](contentLength)
        in.read(bodyChars, 0, contentLength)
        val body = new String(bodyChars)

        // Parse request and execute tool
        val response = handleToolRequest(body)

        // Send HTTP response
        val responseBody = write(response)
        out.println("HTTP/1.1 200 OK")
        out.println("Content-Type: application/json")
        out.println(s"Content-Length: ${responseBody.length}")
        out.println()
        out.print(responseBody)
        out.flush()
      } else {
        // Send 404 for non-POST requests
        out.println("HTTP/1.1 404 Not Found")
        out.println("Content-Length: 0")
        out.println()
        out.flush()
      }
    } finally {
      clientSocket.close()
    }

  /** Handle a tool execution request */
  private def handleToolRequest(jsonBody: String): ToolResponse =
    try {
      val request = read[ToolRequest](jsonBody)

      tools.find(_.name == request.toolName) match {
        case Some(tool) =>
          if state.verbose then
            println(s"[Tool Server] Executing tool '${request.toolName}'")
            println(s"  Arguments: ${request.arguments}")
            
          tool.executeJson(request.arguments) match {
            case Success(result) =>
              ToolResponse(result, success = true)
            case Failure(ex) =>
              ToolResponse("", success = false, error = Some(ex.getMessage))
          }
        case None =>
          if state.verbose then
            println(s"[Tool Server] Tool '${request.toolName}' not found")
            println(s"  Arguments: ${request.arguments}")
          ToolResponse("", success = false, error = Some(s"Tool '${request.toolName}' not found"))
      }
    } catch {
      case ex: Exception =>
        if state.verbose then
          println(s"[Tool Server] Invalid request - ${ex.getMessage}")
        ToolResponse("", success = false, error = Some(s"Invalid request: ${ex.getMessage}"))
    }

  /** Stop the server */
  override def close(): Unit = 
    serverSocket.foreach { socket =>
      socket.close()
    }
    serverThread.foreach { thread =>
      thread.interrupt()
    }
  