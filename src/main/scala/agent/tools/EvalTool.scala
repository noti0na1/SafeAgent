package agent.tools

import agent.core.{Tool, ToolDataType, ToolBase, State}
import upickle.default.*
import scala.util.{Try, Success}
import scala.sys.process.*

/**
 *  Input parameters for Scala code evaluation.
 *
 *  @param code Scala code snippet to evaluate. Can include function calls to other tools.
 */
case class EvalInput(
  code: String
) derives ToolDataType

/**
 *  Result from evaluating Scala code.
 *
 *  @param output The output from the REPL evaluation
 *  @param success Whether the evaluation succeeded
 */
case class EvalOutput(
  output: String,
  success: Boolean
) derives ToolDataType

/**
 *  Tool for evaluating Scala code snippets using scala3-repl.
 *
 *  This tool runs Scala code in an isolated REPL environment and returns the result.
 *  The code can include expressions, definitions, and even call other available tools
 *  via the `callTool` function that is automatically injected into the REPL environment.
 */
class EvalTool(availableTools: List[ToolBase]) extends Tool[EvalInput, EvalOutput]:
  override val name: String = "eval"

  override val description: String =
    """Evaluates Scala 3 code snippets. Can execute Scala expressions, definitions, and function calls.
      |The final result need to be printed to the console to be captured as output.
      |The REPL environment includes a callTool(toolName: String, arguments: String) function
      |that allows calling other agent tools. Example: callTool("calculator", "{\"operation\":\"add\",\"a\":5,\"b\":3}")""".stripMargin

  override def invoke(input: EvalInput)(using state: State): Try[EvalOutput] =
    Try {
      // Start the tool server
      val toolServer = new ToolServer(
        availableTools.filter(_ ne this), // Exclude itself to prevent recursion
        port = 0
      )
      val serverPort = toolServer.start().get

      try {
        // Create a temporary file with the code to evaluate
        val tempFile = java.io.File.createTempFile("scala-eval-", ".sc")
        // For debug, save the file to the current directory
        // val tempFile = java.io.File.createTempFile("scala-eval-", ".sc", new java.io.File("."))
        try {
          // Write user code to the temp file
          val writer = new java.io.PrintWriter(tempFile)
          try {
            writer.write(input.code)
          } finally {
            writer.close()
          }

          // Execute scala with the library file and the user code
          val processBuilder = Process(
            Seq("scala", "library/ToolClient.scala", tempFile.getAbsolutePath),
            None,
            "TOOL_SERVER_PORT" -> serverPort.toString
          )
          val outputBuffer = new StringBuilder
          val errorBuffer = new StringBuilder

          val processLogger = ProcessLogger(
            out => outputBuffer.append(out).append("\n"),
            err => errorBuffer.append(err).append("\n")
          )

          val exitCode = processBuilder.!(processLogger)

          val output = if outputBuffer.nonEmpty then outputBuffer.toString.trim else errorBuffer.toString.trim

          EvalOutput(
            output = if output.isEmpty then "(no output)" else output,
            success = exitCode == 0
          )
        } finally {
          // Clean up temp file
          tempFile.delete()
        }
      } finally {
        // Stop the tool server
        toolServer.stop()
      }
    }.recoverWith { case ex: Throwable =>
      Success(EvalOutput(
        output = s"Error executing Scala code: ${ex.getMessage}",
        success = false
      ))
    }

