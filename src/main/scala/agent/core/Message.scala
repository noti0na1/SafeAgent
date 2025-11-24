package agent.core

import upickle.default.*

/**
 *  Represents different message roles in a conversation.
 *
 *  Each role indicates who or what created the message in the conversation flow.
 */
enum Role:
  case User, Assistant, System, Tool

  /** Convert role to OpenAI API format string */
  def toOpenAI: String = this match
    case User => "user"
    case Assistant => "assistant"
    case System => "system"
    case Tool => "tool"

object Role:
  given ReadWriter[Role] = readwriter[String].bimap[Role](
    role => role.toOpenAI,
    str => str match
      case "user" => User
      case "assistant" => Assistant
      case "system" => System
      case "tool" => Tool
      case _ => throw new IllegalArgumentException(s"Unknown role: $str")
  )

/**
 *  Represents a tool call requested by the assistant.
 *
 *  @param id Unique identifier for this tool call
 *  @param name Name of the tool to invoke
 *  @param arguments JSON string containing the tool's input parameters
 */
case class ToolCall(
  id: String,
  name: String,
  arguments: String
)

/**
 *  Represents a message in the conversation.
 *
 *  @param role Who or what created this message
 *  @param content The text content of the message
 *  @param toolCalls Optional list of tool calls (only for assistant messages)
 *  @param toolCallId Optional ID linking a tool result to its call (only for tool messages)
 *  @param name Optional name identifier (only for tool messages)
 */
case class Message(
  role: Role,
  content: Option[String],
  toolCalls: Option[List[ToolCall]] = None,
  toolCallId: Option[String] = None,
  name: Option[String] = None
):
  /** Format the message as a readable string */
  def toFormattedString(maxContentLength: Int = 500): String =
    val builder = new StringBuilder()

    builder.append(s"Role: $role\n")

    content.foreach { c =>
      val displayContent = if c.length > maxContentLength then
        c.take(maxContentLength) + "..."
      else
        c
      builder.append(s"Content: $displayContent\n")
    }

    toolCalls.foreach { calls =>
      builder.append(s"Tool Calls (${calls.length}):\n")
      calls.foreach { tc =>
        builder.append(s"  - ${tc.name} (id: ${tc.id})\n")
        builder.append(s"    Arguments: ${tc.arguments}\n")
      }
    }

    toolCallId.foreach { id =>
      builder.append(s"Tool Call ID: $id\n")
    }

    name.foreach { n =>
      builder.append(s"Name: $n\n")
    }

    builder.toString()


object Message:
  def user(content: String): Message =
    Message(Role.User, Some(content))

  def assistant(content: String): Message =
    Message(Role.Assistant, Some(content))

  def system(content: String): Message =
    Message(Role.System, Some(content))

  def tool(content: String, toolCallId: String, name: String): Message =
    Message(Role.Tool, Some(content), toolCallId = Some(toolCallId), name = Some(name))

  def assistantWithTools(content: Option[String], toolCalls: List[ToolCall]): Message =
    Message(Role.Assistant, content, Some(toolCalls))
