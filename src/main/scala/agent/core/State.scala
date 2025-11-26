package agent.core

import upickle.default.*
import agent.core.State.Key
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.io.IOException
import scala.util.{Try, Success, Failure}
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.{Map => ImmutableMap}

object State:
  class Key[V](val name: String, val default: () => V):
    val persistent: Boolean = false
  class PersistentKey[V: ReadWriter](name: String, default: () => V) extends Key[V](name, default):
    override val persistent: Boolean = true
    def getRW: ReadWriter[V] = summon[ReadWriter[V]]
  
class State:
  var verbose: Boolean = false

  private val storage: MutableMap[Key[?], Any] = MutableMap.empty

  def set[T](key: State.Key[T], value: T): Unit =
    storage(key) = value

  def get[T](key: State.Key[T]): T =
    storage.getOrElseUpdate(key, key.default()).asInstanceOf[T]

  def getOrElse[T](key: State.Key[T], default: T): T =
    storage.getOrElseUpdate(key, default).asInstanceOf[T]

  def getPersistentKeys: List[Key[?]] =
    storage.keys.filter(_.persistent).toList

  def getAllKeys: List[Key[?]] =
    storage.keys.toList

  private type PersistedData = ImmutableMap[String, String]

  /**
   * Save all persistent state values to a JSON file.
   * Only keys marked as persistent (PersistentKey) will be saved.
   *
   * @param filePath Path to the file where state should be saved
   * @return Try[Unit] indicating success or failure
   */
  def saveToFile(filePath: String): Try[Unit] = Try:
    val persistentData: PersistedData = storage
      .filter { case (key, _) => key.persistent }
      .map { case (key: State.PersistentKey[t], value) =>
        // Serialize each value as JSON string
        key.name -> write(value.asInstanceOf[t])(using key.getRW)
      }
      .toMap

    val jsonString = write(persistentData, indent = 2)
    val path = Paths.get(filePath)

    // Create parent directories if they don't exist
    Option(path.getParent).foreach(Files.createDirectories(_))

    Files.writeString(path, jsonString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  /**
   * Load persistent state values from a JSON file.
   * Only updates keys that are marked as persistent and already exist in storage.
   *
   * @param filePath Path to the file from which state should be loaded
   * @param keys List of persistent keys to potentially load
   * @return Try[Unit] indicating success or failure
   */
  def loadFromFile(filePath: String, keys: List[Key[?]]): Try[Unit] = Try:
    val path = Paths.get(filePath)

    if !Files.exists(path) then
      // File doesn't exist yet, this is normal on first run
      ()
    else
      val jsonString = Files.readString(path)
      val persistentData = read[PersistedData](jsonString)

      // Create a map of key names to keys for quick lookup
      val keysByName: ImmutableMap[String, State.PersistentKey[?]] =
        keys.filter(_.persistent).map {
          case k: State.PersistentKey[?] => k.name -> k
        }.toMap

      persistentData.foreach { case (keyName, jsonValue) =>
        keysByName.get(keyName).foreach { case key: State.PersistentKey[t] =>
          try {
            // Deserialize the value from JSON
            val value = read[t](jsonValue)(using key.getRW)
            storage(key) = value
          } catch {
            case e: Exception =>
              System.err.println(s"Warning: Failed to load persistent key '$keyName': ${e.getMessage}")
          }
        }
      }