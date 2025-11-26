package agent.tools

import agent.core.{Tool, ToolDataType, ToolBase, State}
import upickle.default.*
import scala.util.{Try, Success, Failure, Using}
import scala.sys.process.*
import scala.concurrent.{Future, Await, TimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import java.io.{File, PrintWriter}


object EvalTool:
  /** Generate the complete tool library with type-safe wrappers */
  def generateToolLibrary(tools: List[ToolBase]): String =
    val builder = new StringBuilder() 
    builder.append("import upickle.default.*\n\n")
    // Generate wrappers for each tool
    tools.foreach { tool =>
      builder.append(s"// --- ${tool.name} ---\n")
      builder.append(tool.generateScalaWrapper)
      builder.append("\n")
    }
    builder.toString()

  def isEvalRelatedTool(tool: ToolBase): Boolean = 
    tool.isInstanceOf[EvalTool] || tool.isInstanceOf[GetToolLibraryTool]

case class GetToolLibraryOutput(
  library: String
) derives ToolDataType

/**
 *  Tool for getting the generated tool library code.
 *  Helps LLM understand what functions are available when writing Scala snippets for eval.
 */
class GetToolLibraryTool(availableTools: List[ToolBase]) extends Tool[Unit, GetToolLibraryOutput]:
  override val name: String = "get_tool_library"

  override val description: String =
    """Returns the generated Scala code for the tool library.
      |Use this to see what wrapper functions and types are available
      |when writing Scala 3 code snippets for the eval tool.""".stripMargin

  override def invoke(input: Unit)(using state: State): Try[GetToolLibraryOutput] =
    val tools = availableTools.filter(!EvalTool.isEvalRelatedTool(_))
    Try(GetToolLibraryOutput(EvalTool.generateToolLibrary(tools)))

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
  exitCode: Int
) derives ToolDataType

/**
 *  Tool for evaluating Scala code snippets using scala3-repl.
 *
 *  This tool runs Scala code in an isolated REPL environment and returns the result.
 *  The code can include expressions, definitions, and even call other available tools
 *  via the `callTool` function that is automatically injected into the REPL environment.
 *
 *  @param availableTools List of tools available for the eval environment
 *  @param timeout Maximum execution time (default: 60 seconds)
 */
class EvalTool(availableTools: List[ToolBase], timeout: Duration = 2.minutes) extends Tool[EvalInput, EvalOutput]:
  override val name: String = "eval"

  override val description: String =
    """Evaluates Scala 3 code snippets. Can execute Scala expressions, definitions, and function calls.
      |The final result need to be printed to the console to be captured as output.
      |
      |Use "get_tool_library" tool first to see the available tool functions and their signatures.
      |Example: val result = calculator(operation = "add", a = 5.0, b = 3.0)
      |         println(result.result)  // prints result""".stripMargin

  /** Resource wrapper for temp files that auto-delete */
  private class TempFile(prefix: String, suffix: String) extends AutoCloseable:
    val file: File = File.createTempFile(prefix, suffix)
    def write(content: String): Unit = Using.resource(new PrintWriter(file))(_.write(content))
    def absolutePath: String = file.getAbsolutePath
    override def close(): Unit = file.delete()

  override def invoke(input: EvalInput)(using state: State): Try[EvalOutput] =
    val tools = availableTools.filter(!EvalTool.isEvalRelatedTool(_))
    val wrapperLibrary = EvalTool.generateToolLibrary(tools)

    val port = sys.env.getOrElse("TOOL_SERVER_PORT", "8080").toInt

    Using.Manager { use =>
      val server = use(new ToolServer(tools, port))
      val libraryFile = use(new TempFile("tool-library-", ".scala"))
      val codeFile = use(new TempFile("scala-eval-", ".sc"))
    
      libraryFile.write(wrapperLibrary)
      codeFile.write(input.code)
      server.start()
      executeScala(port, libraryFile.absolutePath, codeFile.absolutePath)
    }

  private def executeScala(serverPort: Int, libraryPath: String, codePath: String): EvalOutput =
    val outputBuffer = new StringBuilder
    val errorBuffer = new StringBuilder

    val processLogger = ProcessLogger(
      out => outputBuffer.append(out).append("\n"),
      err => errorBuffer.append(err).append("\n")
    )

    val process = Process(
      Seq("scala", "library/ToolCallClient.scala", libraryPath, codePath),
      None,
      "TOOL_SERVER_PORT" -> serverPort.toString
    ).run(processLogger)

    // Run with timeout
    val exitCodeFuture = Future(process.exitValue())

    val exitCode = try
      Await.result(exitCodeFuture, timeout)
    catch
      case _: TimeoutException =>
        process.destroy()
        errorBuffer.append(s"\nProcess timed out after ${timeout.toSeconds} seconds")
        -1

    val output = if outputBuffer.nonEmpty then outputBuffer.toString.trim else errorBuffer.toString.trim

    EvalOutput(
      output = if output.isEmpty then "(no output)" else output,
      exitCode = exitCode
    )

