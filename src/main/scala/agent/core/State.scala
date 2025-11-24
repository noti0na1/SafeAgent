package agent.core

import scala.collection.mutable.Map
import agent.core.State.Key

object State:
  case class Key[V](name: String, default: Option[V] = None)
  
class State(val agentConfig: AgentConfig):
  private val storage: Map[Key[?], Any] = Map.empty
  
  def set[T](key: State.Key[T], value: T): Unit =
    storage(key) = value

  def get[T](key: State.Key[T]): Option[T] =
    storage.get(key).asInstanceOf[Option[T]].orElse(key.default)

  def getOrElse[T](key: State.Key[T], default: T): T =
    get(key).getOrElse(default)