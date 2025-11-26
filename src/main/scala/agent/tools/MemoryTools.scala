package agent.tools

import agent.core.{Tool, ToolBase, ToolDataType, State}
import agent.core.ToolDataType.toolDataTypeToJsonSchema
import agent.core.ToolDataType.toolDataTypeToReadWriter

import upickle.default.*
import scala.util.Try
import scala.collection.mutable

// Shared memory key for all memory tools
object MemoryTools:
  val memoryKey = State.PersistentKey[mutable.Map[String, String]]("agent_memory", () => mutable.Map.empty)

  def allTools: List[ToolBase] =
    List(
      new StoreMemoryTool(),
      new RetrieveMemoryTool(),
      new QueryMemoryTool(),
      new ListMemoryTool(),
    )

  def getMemory(using state: State): mutable.Map[String, String] =
    state.get(memoryKey)

// ============================================================================
// Store Memory Tool
// ============================================================================

/**
 *  Input for storing a memory.
 *
 *  @param key The key to store the memory under
 *  @param value The value to store
 */
case class StoreMemoryInput(
  key: String,
  value: String
) derives ToolDataType

/**
 *  Tool for storing a key-value pair in memory.
 *
 *  This allows the agent to persist information in the State that can be
 *  retrieved in later interactions.
 */
class StoreMemoryTool extends Tool[StoreMemoryInput, Unit]:
  override val name: String = "store_memory"

  override val description: String =
    "Store a key-value pair in memory for later retrieval. Use this to remember important information across interactions."

  override def invoke(input: StoreMemoryInput)(using state: State): Try[Unit] =
    Try {
      val memory = MemoryTools.getMemory
      memory(input.key) = input.value
    }

// ============================================================================
// Retrieve Memory Tool
// ============================================================================

/**
 *  Input for retrieving a memory.
 *
 *  @param key The key to retrieve
 */
case class RetrieveMemoryInput(
  key: String
) derives ToolDataType

/**
 *  Output from retrieving a memory.
 *
 *  @param key The key that was requested
 *  @param value The retrieved value (if found)
 *  @param found Whether the key was found
 */
case class RetrieveMemoryOutput(
  key: String,
  value: Option[String],
  found: Boolean
) derives ToolDataType

/**
 *  Tool for retrieving a value from memory by key.
 */
class RetrieveMemoryTool extends Tool[RetrieveMemoryInput, RetrieveMemoryOutput]:
  override val name: String = "retrieve_memory"

  override val description: String =
    "Retrieve a value from memory by its key. Returns the stored value if it exists."

  override def invoke(input: RetrieveMemoryInput)(using state: State): Try[RetrieveMemoryOutput] =
    Try {
      val memory = MemoryTools.getMemory

      memory.get(input.key) match
        case Some(value) =>
          RetrieveMemoryOutput(
            key = input.key,
            value = Some(value),
            found = true
          )
        case None =>
          RetrieveMemoryOutput(
            key = input.key,
            value = None,
            found = false
          )
    }

// ============================================================================
// List Memory Keys Tool
// ============================================================================

/**
 *  Output from listing memory keys.
 *
 *  @param keys List of all memory keys
 *  @param count Number of keys found
 */
case class ListMemoryOutput(
  keys: List[String],
  count: Int
) derives ToolDataType

/**
 *  Tool for listing all available memory keys.
 */
class ListMemoryTool extends Tool[Unit, ListMemoryOutput]:
  override val name: String = "list_memory"

  override val description: String =
    "List all available memory keys that have been stored."

  override def invoke(input: Unit)(using state: State): Try[ListMemoryOutput] =
    Try {
      val memory = MemoryTools.getMemory
      val keys = memory.keys.toList.sorted

      ListMemoryOutput(
        keys = keys,
        count = keys.size
      )
    }

// ============================================================================
// Query Memory Tool
// ============================================================================

/**
 *  Input for querying memory.
 *
 *  @param pattern The search pattern (string or regex)
 *  @param isRegex Whether to treat the pattern as a regex (default: false)
 *  @param searchKeys Whether to search in keys (default: true)
 *  @param searchValues Whether to search in values (default: true)
 */
case class QueryMemoryInput(
  pattern: String,
  isRegex: Boolean = false,
  searchKeys: Boolean = true,
  searchValues: Boolean = true
) derives ToolDataType

/**
 *  A single match from the query.
 *
 *  @param key The key where the match was found
 *  @param value The value associated with the key
 *  @param matchedIn Where the match was found ("key", "value", or "both")
 */
case class QueryMemoryMatch(
  key: String,
  value: String,
  matchedIn: String
) derives ToolDataType

/**
 *  Output from querying memory.
 *
 *  @param matches List of matching entries
 *  @param count Number of matches found
 *  @param pattern The pattern that was searched
 */
case class QueryMemoryOutput(
  matches: List[QueryMemoryMatch],
  count: Int,
  pattern: String
) derives ToolDataType

/**
 *  Tool for searching memory by string or regex pattern.
 *
 *  This allows the agent to find stored information by searching
 *  through keys and/or values using plain text or regex patterns.
 */
class QueryMemoryTool extends Tool[QueryMemoryInput, QueryMemoryOutput]:
  override val name: String = "query_memory"

  override val description: String =
    "Search memory for entries matching a pattern. Supports plain text search or regex. Can search in keys, values, or both."

  override def invoke(input: QueryMemoryInput)(using state: State): Try[QueryMemoryOutput] =
    Try {
      val memory = MemoryTools.getMemory

      val matcher: String => Boolean = if input.isRegex then
        val regex = input.pattern.r
        (s: String) => regex.findFirstIn(s).isDefined
      else
        (s: String) => s.contains(input.pattern)

      val matches = memory.toList.flatMap { case (key, value) =>
        val keyMatches = input.searchKeys && matcher(key)
        val valueMatches = input.searchValues && matcher(value)

        if keyMatches || valueMatches then
          val matchedIn = (keyMatches, valueMatches) match
            case (true, true) => "both"
            case (true, false) => "key"
            case (false, true) => "value"
            case _ => "none"
          Some(QueryMemoryMatch(key, value, matchedIn))
        else
          None
      }.sortBy(_.key)

      QueryMemoryOutput(
        matches = matches,
        count = matches.size,
        pattern = input.pattern
      )
    }
