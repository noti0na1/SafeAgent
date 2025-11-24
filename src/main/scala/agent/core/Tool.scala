package agent.core

import upickle.default.*
import scala.util.{Try, Success, Failure}
import scala.deriving.Mirror
import scala.reflect.ClassTag
import scala.compiletime.{erasedValue, summonInline, constValue}

/** Type class for tool data types that can be serialized and have JSON schemas */
trait ToolDataType[T]:
  def schema: Map[String, Any]
  def required: List[String]
  def readWriter: ReadWriter[T]

object ToolDataType:
  /** Create a ToolDataType instance from JsonSchema and ReadWriter */
  def apply[T](jsonSchema: JsonSchema[T], rw: ReadWriter[T]): ToolDataType[T] =
    new ToolDataType[T]:
      def schema: Map[String, Any] = jsonSchema.schema
      def required: List[String] = jsonSchema.required
      def readWriter: ReadWriter[T] = rw

  /** Derive ToolDataType for case classes */
  inline def derived[T: ClassTag](using mirror: Mirror.ProductOf[T]): ToolDataType[T] =
    val rw = macroRW[T]
    val jsonSchema = JsonSchema.derived[T]
    apply(jsonSchema, rw)

  /** Automatically provide ReadWriter from ToolDataType */
  given toolDataTypeToReadWriter[T](using tdt: ToolDataType[T]): ReadWriter[T] =
    tdt.readWriter

  /** Automatically provide JsonSchema from ToolDataType */
  given toolDataTypeToJsonSchema[T](using tdt: ToolDataType[T]): JsonSchema[T] with
    def schema: Map[String, Any] = tdt.schema
    def required: List[String] = tdt.required

  /** Special ToolDataType for Unit - represents no arguments or no result */
  given ToolDataType[Unit] = new ToolDataType[Unit]:
    def schema: Map[String, Any] = Map("type" -> "object", "properties" -> Map.empty[String, Any])
    def required: List[String] = Nil
    def readWriter: ReadWriter[Unit] = readwriter[Unit].bimap(_ => (), _ => ())

/** Type class for generating JSON schemas from types */
trait JsonSchema[T]:
  def schema: Map[String, Any]
  def required: List[String]

object JsonSchema:
  // Basic type instances
  given JsonSchema[String]:
    def schema: Map[String, Any] = Map("type" -> "string")
    def required: List[String] = Nil

  given JsonSchema[Int]:
    def schema: Map[String, Any] = Map("type" -> "integer")
    def required: List[String] = Nil

  given JsonSchema[Long]:
    def schema: Map[String, Any] = Map("type" -> "integer")
    def required: List[String] = Nil

  given JsonSchema[Double]:
    def schema: Map[String, Any] = Map("type" -> "number")
    def required: List[String] = Nil

  given JsonSchema[Float]:
    def schema: Map[String, Any] = Map("type" -> "number")
    def required: List[String] = Nil

  given JsonSchema[Boolean]:
    def schema: Map[String, Any] = Map("type" -> "boolean")
    def required: List[String] = Nil

  given JsonSchema[Unit]:
    def schema: Map[String, Any] = Map("type" -> "object", "properties" -> Map.empty[String, Any])
    def required: List[String] = Nil

  given [T](using innerSchema: JsonSchema[T]): JsonSchema[Option[T]] with
    def schema: Map[String, Any] = innerSchema.schema
    def required: List[String] = Nil

  given [T](using itemSchema: JsonSchema[T]): JsonSchema[List[T]] with
    def schema: Map[String, Any] = Map(
      "type" -> "array",
      "items" -> itemSchema.schema
    )
    def required: List[String] = Nil

  // Derive JSON schema for case classes
  inline def derived[T](using mirror: Mirror.ProductOf[T]): JsonSchema[T] =
    val labels = getFieldLabels[mirror.MirroredElemLabels]
    val schemas = getFieldSchemas[mirror.MirroredElemTypes]
    val properties = labels.zip(schemas).toMap
    val requiredFields = getRequiredFields[mirror.MirroredElemTypes, mirror.MirroredElemLabels]

    DerivedJsonSchema[T](properties, requiredFields)

  // Concrete implementation to avoid anonymous class duplication
  private[core] class DerivedJsonSchema[T](
    properties: Map[String, Map[String, Any]],
    requiredFields: List[String]
  ) extends JsonSchema[T]:
    def schema: Map[String, Any] = Map("type" -> "object", "properties" -> properties)
    def required: List[String] = requiredFields

  // Helper to get field labels
  inline def getFieldLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts) => constValue[t].asInstanceOf[String] :: getFieldLabels[ts]

  // Helper to get field schemas
  inline def getFieldSchemas[T <: Tuple]: List[Map[String, Any]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts) =>
        summonInline[JsonSchema[t]].schema :: getFieldSchemas[ts]

  // Helper to get required fields (fields that are not Option)
  inline def getRequiredFields[Types <: Tuple, Labels <: Tuple]: List[String] =
    inline erasedValue[(Types, Labels)] match
      case _: (EmptyTuple, EmptyTuple) => Nil
      case _: ((Option[t] *: ts), (label *: labels)) =>
        getRequiredFields[ts, labels]
      case _: ((t *: ts), (label *: labels)) =>
        constValue[label].asInstanceOf[String] :: getRequiredFields[ts, labels]

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

    if state.agentConfig.verbose then
      println(s"[Tool Call] $name")
      println(s"  Arguments: ${write[Input](input)}")
      result match
        case Success(output) =>
          if state.agentConfig.verbose then
            println(s"  Result: ${write[Output](output)}")
        case Failure(ex) =>
          if state.agentConfig.verbose then
            println(s"  Error: ${ex.getMessage}")

    result

  /** Implement this method to define the tool's functionality */
  def invoke(input: Input)(using state: State): Try[Output]

  given ReadWriter[Input] = summon[ToolDataType[Input]].readWriter
  given ReadWriter[Output] = summon[ToolDataType[Output]].readWriter

  /** Execute the tool with JSON string arguments */
  final override def executeJson(arguments: String)(using State): Try[String] =
    for
      // Handle Unit input: if arguments are empty or "{}", parse as Unit
      input <- Try {
        val normalizedArgs = if arguments.trim.isEmpty then "()" else arguments
        read[Input](normalizedArgs)
      }
      output <- execute(input)
    yield
      // Handle Unit output: return empty object instead of "()"
      val result = write[Output](output)
      if result.trim == "()" then "{}" else result

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
