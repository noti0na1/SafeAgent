package agent.core

import upickle.default.*
import scala.util.{Try, Success, Failure}
import scala.reflect.ClassTag

/** Base trait for all tools (type-erased interface) */
trait ToolBase:
  /** Unique name of the tool */
  def name: String

  /** Description of what the tool does */
  def description: String

  /** Execute the tool with JSON string arguments */
  def executeJson(arguments: String)(using State): Try[String]

  /** Convert tool definition to OpenAI function format */
  def toRawFunction: Map[String, Any]

  /** Get full description including parameters */
  def getFullDescription: String =
    val builder = new StringBuilder()
    builder.append(s"\n  $name:\n")
    builder.append(s"    $description\n")

    // Extract nested maps safely
    val toolDef = toRawFunction
    for {
      function <- toolDef.get("function").collect { case m: Map[String @unchecked, Any @unchecked] => m }
      parameters <- function.get("parameters").collect { case m: Map[String @unchecked, Any @unchecked] => m }
    } {
      val properties = parameters.get("properties").collect {
        case m: Map[String @unchecked, Map[String @unchecked, Any @unchecked] @unchecked] => m
      }.getOrElse(Map.empty)

      val required = parameters.get("required").collect {
        case l: List[String @unchecked] => l
      }.getOrElse(List.empty)

      builder.append(s"    Parameters:\n")
      properties.foreach { case (name, props) =>
        val paramType = props.getOrElse("type", "unknown")
        val requiredTag = if required.contains(name) then "[required]" else "[optional]"
        val description = props.getOrElse("description", "")
        builder.append(s"      - $name ($paramType) $requiredTag: $description\n")
      }
    }

    builder.toString()

/** Trait for defining typed tools with input and output types */
abstract class Tool[Input: ToolDataType, Output: ToolDataType] extends ToolBase:

  /** Execute the tool with the given typed input */
  final def execute(input: Input)(using state: State): Try[Output] =
    val result = invoke(input)(using state)
    
    if state.verbose then
      println(s"[Tool Call] $name")
      println(s"  Arguments: ${write[Input](input)}")
      result match
        case Success(output) =>
          if state.verbose then
            println(s"  Result: ${write[Output](output)}")
        case Failure(ex) =>
          if state.verbose then
            println(s"  Error: ${ex.getMessage}")

    result

  /** Implement this method to define the tool's functionality */
  def invoke(input: Input)(using state: State): Try[Output]

  given ReadWriter[Input] = summon[ToolDataType[Input]].readWriter
  given ReadWriter[Output] = summon[ToolDataType[Output]].readWriter

  /** Execute the tool with JSON string arguments */
  final override def executeJson(arguments: String)(using State): Try[String] =
    for
      // Handle Unit input: if arguments are empty, parse as `{}`
      input <- Try {
        val normalizedArgs = if arguments.trim.isEmpty then "{}" else arguments
        read[Input](normalizedArgs)
      }
      output <- execute(input)
    yield
      write[Output](output)

  /** Convert tool definition to OpenAI function format */
  final override def toRawFunction: Map[String, Any] =
    val inputDataType = summon[ToolDataType[Input]]
    val inputSchemaMap = inputDataType.schema
    val properties = inputSchemaMap.getOrElse("properties", Map.empty).asInstanceOf[Map[String, Any]]
    val required = inputDataType.required

    // Build the parameters map, handling Unit type (empty properties)
    val parameters = Map(
      "type" -> "object",
      "properties" -> properties,
      "required" -> required
    )

    Map(
      "type" -> "function",
      "function" -> Map(
        "name" -> name,
        "description" -> description,
        "parameters" -> parameters
      )
    )

/** Result of executing a tool */
case class ToolResult(
  toolCallId: String,
  toolName: String,
  result: String,
  success: Boolean
) derives ReadWriter
