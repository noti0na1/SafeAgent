package agent.tools

import agent.core.{Tool, ToolBase, ToolDataType, State}
import upickle.default.*
import scala.util.{Try, Success, Failure}
import scala.collection.mutable

// Shared memory key for all memory tools
object MemoryTools:
  val memoryKey = State.Key[mutable.Map[String, String]]("agent_memory", Some(mutable.Map.empty))

  def allTools: List[ToolBase] =
    List(
      new StoreMemoryTool(),
      new RetrieveMemoryTool(),
      new ListMemoryTool()
    )

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
 *  Output from storing a memory.
 *
 *  @param key The key that was stored
 *  @param value The value that was stored
 *  @param message Status message
 */
case class StoreMemoryOutput(
  key: String,
  value: String,
  message: String
) derives ToolDataType

/**
 *  Tool for storing a key-value pair in memory.
 *
 *  This allows the agent to persist information in the State that can be
 *  retrieved in later interactions.
 */
class StoreMemoryTool extends Tool[StoreMemoryInput, StoreMemoryOutput]:
  override val name: String = "store_memory"

  override val description: String =
    "Store a key-value pair in memory for later retrieval. Use this to remember important information across interactions."

  override def invoke(input: StoreMemoryInput)(using state: State): Try[StoreMemoryOutput] =
    Try {
      val memory = state.getOrElse(MemoryTools.memoryKey, mutable.Map.empty[String, String])
      memory(input.key) = input.value
      state.set(MemoryTools.memoryKey, memory)

      StoreMemoryOutput(
        key = input.key,
        value = input.value,
        message = s"Successfully stored value for key '${input.key}'"
      )
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
 *  @param message Status message
 */
case class RetrieveMemoryOutput(
  key: String,
  value: Option[String],
  found: Boolean,
  message: String
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
      val memory = state.getOrElse(MemoryTools.memoryKey, mutable.Map.empty[String, String])

      memory.get(input.key) match
        case Some(value) =>
          RetrieveMemoryOutput(
            key = input.key,
            value = Some(value),
            found = true,
            message = s"Successfully retrieved value for key '${input.key}'"
          )
        case None =>
          RetrieveMemoryOutput(
            key = input.key,
            value = None,
            found = false,
            message = s"No value found for key '${input.key}'"
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
 *  @param message Status message
 */
case class ListMemoryOutput(
  keys: List[String],
  count: Int,
  message: String
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
      val memory = state.getOrElse(MemoryTools.memoryKey, mutable.Map.empty[String, String])
      val keys = memory.keys.toList.sorted

      ListMemoryOutput(
        keys = keys,
        count = keys.size,
        message = if keys.isEmpty then "No memories stored" else s"Found ${keys.size} memory key(s)"
      )
    }
