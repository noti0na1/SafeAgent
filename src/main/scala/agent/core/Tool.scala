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

  /** Generate Scala case class and wrapper function code for type-safe tool calling */
  def generateScalaWrapper: String

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

  /** Generate Scala case class and wrapper function code for type-safe tool calling */
  final override def generateScalaWrapper: String =
    val inputDataType = summon[ToolDataType[Input]]
    val outputDataType = summon[ToolDataType[Output]]
    val inputSchemaMap = inputDataType.schema
    val outputSchemaMap = outputDataType.schema
    val properties = inputSchemaMap.getOrElse("properties", Map.empty).asInstanceOf[Map[String, Map[String, Any]]]
    val requiredFields = inputDataType.required.toSet
    val outputProperties = outputSchemaMap.getOrElse("properties", Map.empty).asInstanceOf[Map[String, Map[String, Any]]]

    val builder = new StringBuilder()

    // Generate input case class (if has parameters)
    val inputClassName = toPascalCase(name) + "Input"
    val outputClassName = toPascalCase(name) + "Output"

    if properties.nonEmpty then
      builder.append(s"case class $inputClassName(\n")
      val params = properties.map { case (fieldName, fieldProps) =>
        val scalaType = jsonTypeToScala(fieldProps, requiredFields.contains(fieldName))
        s"  $fieldName: $scalaType"
      }
      builder.append(params.mkString(",\n"))
      builder.append("\n) derives ReadWriter\n\n")

    // Generate output case class (if has fields)
    if outputProperties.nonEmpty then
      builder.append(s"case class $outputClassName(\n")
      val outParams = outputProperties.map { case (fieldName, fieldProps) =>
        val scalaType = jsonTypeToScala(fieldProps, isRequired = true)
        s"  $fieldName: $scalaType"
      }
      builder.append(outParams.mkString(",\n"))
      builder.append("\n) derives ReadWriter\n\n")

    // Generate wrapper function
    val funcName = toScalaFunctionName(name)

    if properties.isEmpty then
      // No input parameters
      val returnType = if outputProperties.isEmpty then "Unit" else outputClassName
      builder.append(s"/** $description */\n")
      builder.append(s"def $funcName(): $returnType = {\n")
      builder.append(s"""  val result = callTool("$name", "{}")\n""")
      if outputProperties.isEmpty then
        builder.append("  ()\n")
      else
        builder.append(s"  read[$returnType](result)\n")
      builder.append("}\n")
    else
      // Has input parameters - generate function with named parameters
      val returnType = if outputProperties.isEmpty then "Unit" else outputClassName
      val funcParams = properties.map { case (fieldName, fieldProps) =>
        val scalaType = jsonTypeToScala(fieldProps, requiredFields.contains(fieldName))
        val defaultValue = if requiredFields.contains(fieldName) then "" else " = None"
        s"$fieldName: $scalaType$defaultValue"
      }

      builder.append(s"/** $description */\n")
      builder.append(s"def $funcName(${funcParams.mkString(", ")}): $returnType = {\n")
      builder.append(s"  val input = $inputClassName(${properties.keys.mkString(", ")})\n")
      builder.append(s"""  val result = callTool("$name", write(input))\n""")
      if outputProperties.isEmpty then
        builder.append("  ()\n")
      else
        builder.append(s"  read[$returnType](result)\n")
      builder.append("}\n")

    builder.toString()

  /** Convert JSON schema type to Scala type */
  private def jsonTypeToScala(props: Map[String, Any], isRequired: Boolean): String =
    val baseType = props.get("type") match
      case Some("string") => "String"
      case Some("integer") => "Int"
      case Some("number") => "Double"
      case Some("boolean") => "Boolean"
      case Some("array") =>
        val itemType = props.get("items") match
          case Some(items: Map[String @unchecked, Any @unchecked]) =>
            jsonTypeToScala(items, isRequired = true)
          case _ => "Any"
        s"List[$itemType]"
      case Some("object") => "ujson.Value"
      case _ => "Any"

    if isRequired then baseType else s"Option[$baseType]"

  /** Convert tool name to PascalCase for class names */
  private def toPascalCase(s: String): String =
    s.split("[_-]").map(_.capitalize).mkString

  /** Convert tool name to camelCase for function names */
  private def toScalaFunctionName(s: String): String =
    val parts = s.split("[_-]")
    if parts.isEmpty then s
    else parts.head + parts.tail.map(_.capitalize).mkString

/** Result of executing a tool */
case class ToolResult(
  toolCallId: String,
  toolName: String,
  result: String,
  success: Boolean
) derives ReadWriter
