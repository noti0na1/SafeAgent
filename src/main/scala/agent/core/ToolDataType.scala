package agent.core

import upickle.default.*
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
    def schema: Map[String, Any] = Map.empty
    def required: List[String] = Nil
    def readWriter: ReadWriter[Unit] = readwriter[ujson.Obj].bimap(_ => ujson.Obj(), _ => ())

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