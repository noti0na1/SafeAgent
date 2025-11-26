package agent.tools

import agent.core.{Tool, ToolBase, ToolDataType, State}
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
      new ListMemoryTool()
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
