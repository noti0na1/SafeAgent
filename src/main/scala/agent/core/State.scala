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
    storage.get(key) match
      case Some(value) => Some(value.asInstanceOf[T])
      case None => key.default match
        case Some(defaultValue) => 
          storage(key) = defaultValue
          Some(defaultValue)
        case None => None
      
  def getOrElseUpdate[T](key: State.Key[T], default: T): T =
    storage.getOrElseUpdate(key, default).asInstanceOf[T]