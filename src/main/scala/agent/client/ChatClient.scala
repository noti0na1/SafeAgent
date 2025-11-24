package agent.client

import agent.core.*

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.*
import com.openai.models.*
import com.openai.core.JsonValue

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

/**
 *  Response from a chat completion request.
 *
 *  @param content Optional text content from the assistant
 *  @param toolCalls Optional list of tool calls requested by the assistant
 *  @param finishReason Reason the response ended (e.g., "stop", "tool_calls")
 */
case class ChatResponse(
  content: Option[String],
  toolCalls: Option[List[ToolCall]],
  finishReason: String
):
  /** Check if this response contains any tool calls */
  def hasToolCalls: Boolean = toolCalls.exists(_.nonEmpty)

/**
 *  Simple chat client wrapper for OpenAI-compatible APIs.
 *
 *  This client is independent of the Agent framework and provides
 *  a low-level interface for chat completions with tool calling support.
 */
class ChatClient(
  val config: ModelConfig,
  private val client: OpenAIClient
):
  /** Send a chat request with messages and optional tools */
  def chat(
    messages: List[Message],
    tools: List[ToolBase] = List.empty,
    systemPrompt: Option[String] = None
  ): Try[ChatResponse] =
    Try {
      val builder = ChatCompletionCreateParams.builder()
        .model(config.model)

      // Add system prompt if present
      systemPrompt.foreach(builder.addSystemMessage)

      // Set optional parameters
      config.maxTokens.foreach(t => builder.maxCompletionTokens(t.toLong))
      config.temperature.foreach(builder.temperature)

      // Add tools
      ChatClient.addToolsToBuilder(builder, tools)

      // Add all messages to the builder
      messages.foreach { msg =>
        msg.role match
          case Role.User =>
            builder.addUserMessage(msg.content.getOrElse(""))
          case Role.Assistant =>
            // Check if this assistant message has tool calls
            msg.toolCalls match
              case Some(calls) =>
                // Create assistant message with tool calls
                val assistantMsgBuilder = ChatCompletionAssistantMessageParam.builder()

                // Add content if present
                msg.content.foreach(assistantMsgBuilder.content)

                // Convert and add tool calls
                calls.foreach { call =>
                  val toolCallParam = ChatCompletionMessageFunctionToolCall.builder()
                    .id(call.id)
                    .function(
                      ChatCompletionMessageFunctionToolCall.Function.builder()
                        .name(call.name)
                        .arguments(call.arguments)
                        .build()
                    )
                    .build()
                  assistantMsgBuilder.addToolCall(toolCallParam)
                }

                builder.addMessage(assistantMsgBuilder.build())
              case None =>
                // Simple assistant message without tool calls
                builder.addAssistantMessage(msg.content.getOrElse(""))
          case Role.System =>
            builder.addSystemMessage(msg.content.getOrElse(""))
          case Role.Tool =>
            // Tool messages contain the result of a tool call
            msg.toolCallId.foreach { toolCallId =>
              val toolMsg = ChatCompletionToolMessageParam.builder()
                .toolCallId(toolCallId)
                .content(msg.content.getOrElse(""))
                .build()
              builder.addMessage(toolMsg)
            }
      }

      val params = builder.build()
      val completion = client.chat().completions().create(params)

      // Extract response
      val choice = completion.choices().asScala.headOption
        .getOrElse(throw new RuntimeException("No choices in completion response"))

      val message = choice.message()

      // Handle Optional<String> from Java API
      val content = message.content().toScala
      val finishReason = choice.finishReason().toString

      // Extract tool calls if present
      val toolCalls = Option(message.toolCalls()).flatMap { javaToolCalls =>
        val calls = javaToolCalls.toScala.toList.flatMap(_.asScala)
        if (calls.isEmpty) None
        else Some(calls.map { call =>
          val functionCall = call.asFunction()
          val function = functionCall.function()
          ToolCall(
            id = functionCall.id(),
            name = function.name(),
            // Handle optional arguments field - default to empty JSON object if not present
            arguments = Try(function.arguments()).getOrElse("{}")
          )
        })
      }

      ChatResponse(
        content = content,
        toolCalls = toolCalls,
        finishReason = finishReason
      )
    }

object ChatClient:
  /** Convert a ToolBase to OpenAI's ChatCompletionFunctionTool format */
  private def convertToolToOpenAI(tool: ToolBase): ChatCompletionFunctionTool =
    val toolDef = tool.toRawFunction
    val functionMap = toolDef("function").asInstanceOf[Map[String, Any]]
    val parametersMap = functionMap("parameters").asInstanceOf[Map[String, Any]]
    val propertiesMap = parametersMap("properties").asInstanceOf[Map[String, Any]]
    val requiredList = parametersMap("required").asInstanceOf[List[String]]

    // Convert Scala maps to Java maps for OpenAI SDK
    val javaPropertiesMap = new java.util.HashMap[String, Any]()
    propertiesMap.foreach { case (key, value) =>
      val valueMap = value.asInstanceOf[Map[String, Any]]
      val javaValueMap = new java.util.HashMap[String, Any]()
      valueMap.foreach { case (k, v) => javaValueMap.put(k, v) }
      javaPropertiesMap.put(key, javaValueMap)
    }

    // Create FunctionParameters
    val functionParams = FunctionParameters.builder()
      .putAdditionalProperty("type", JsonValue.from("object"))
      .putAdditionalProperty("properties", JsonValue.from(javaPropertiesMap))
      .putAdditionalProperty("required", JsonValue.from(requiredList.asJava))
      .putAdditionalProperty("additionalProperties", JsonValue.from(false))
      .build()

    // Create FunctionDefinition
    val functionDef = FunctionDefinition.builder()
      .name(tool.name)
      .description(tool.description)
      .parameters(functionParams)
      .build()

    // Create and return ChatCompletionFunctionTool
    ChatCompletionFunctionTool.builder()
      .function(functionDef)
      .build()

  /** Add tools to a builder */
  private def addToolsToBuilder(builder: ChatCompletionCreateParams.Builder, tools: List[ToolBase]): Unit =
    tools.foreach { tool =>
      builder.addTool(convertToolToOpenAI(tool))
    }

  /** Create a new ChatClient */
  def apply(config: ModelConfig): ChatClient =
    val client = OpenAIOkHttpClient.builder()
      .apiKey(config.apiKey)
      .baseUrl(config.baseUrl)
      .build()
    apply(config, client)

  def apply(config: ModelConfig, client: OpenAIClient): ChatClient =
    new ChatClient(config, client)
