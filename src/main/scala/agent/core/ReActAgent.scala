package agent.core

import agent.client.{ChatClient, ModelConfig}
import agent.client.ChatResponse

import scala.util.{Try, Success, Failure}
import scala.annotation.tailrec

/**
 *  ReAct (Reasoning and Acting) agent implementation.
 *
 *  This agent follows the ReAct pattern: it reasons about what to do,
 *  invokes tools as needed, and continues until it can provide a final answer.
 *  The agent uses a ChatClient to communicate with the LLM and manages
 *  the conversation state through iterative tool-calling rounds.
 */
class ReActAgent(
  val config: AgentConfig,
  val tools: List[ToolBase],
  var messages: List[Message],
  private val chatClient: ChatClient
) extends Agent:

  type Response = (ReActAgent, ChatResponse)
  
  given state: State = new State(config)

  /** Create a new instance with updated messages */
  private def withMessages(newMessages: List[Message]): this.type =
    messages = newMessages
    this

  override def addMessage(message: Message): this.type =
    withMessages(messages :+ message)

  override def addMessages(newMessages: List[Message]): this.type =
    withMessages(messages ++ newMessages)

  /** Execute a tool call */
  private def executeTool(toolCall: ToolCall): ToolResult =
    tools.find(_.name == toolCall.name) match
      case Some(tool) =>
        tool.executeJson(toolCall.arguments) match
          case Success(result) =>
            ToolResult(toolCall.id, toolCall.name, result, success = true)
          case Failure(ex) =>
            ToolResult(toolCall.id, toolCall.name, s"Error: ${ex.getMessage}", success = false)
      case None =>
        if state.agentConfig.verbose then
          println(s"[Tool Call] ${toolCall.name}")
          println(s"  Arguments: ${toolCall.arguments}")
          println(s"  Error: Tool not found")
        ToolResult(toolCall.id, toolCall.name, s"Tool '${toolCall.name}' not found", success = false)

  override def run(userMessage: String): Try[String] =
    // Add the user message to the state
    addMessage(Message.user(userMessage))

    @tailrec
    def loop(iteration: Int): Try[String] =
      if iteration >= config.maxIterations then
        Failure(new RuntimeException(s"Max iterations (${config.maxIterations}) reached"))
      else
        // Call ChatClient with the current messages
        chatClient.chat(messages, tools, Option(config.systemPrompt)) match
          case Success(response) =>
            if response.hasToolCalls then
              // Add assistant message with tool calls
              val assistantMsg = Message.assistantWithTools(response.content, response.toolCalls.get)
              addMessage(assistantMsg)

              // Execute all tool calls
              val toolResults = response.toolCalls.get.map { toolCall =>
                executeTool(toolCall)
              }

              // Add tool message
              val toolMessages = toolResults.map { result =>
                Message.tool(result.result, result.toolCallId, result.toolName)
              }
              addMessages(toolMessages)

              // Continue the loop with tool results
              loop(iteration + 1)
            else
              // Add final reponse to messages
              val resultMsg = response.content.getOrElse("")
              addMessage(Message.assistant(resultMsg))
              
              Success(resultMsg)
          case Failure(ex) =>
            Failure(ex)

    loop(0)

object ReActAgent:
  /**
   *  Create a new ReActAgent with a custom ChatClient instance.
   *
   *  Use this when you need to provide a specific ChatClient configuration
   *  or mock client for testing.
   *
   *  @param agentConfig Configuration for agent behavior
   *  @param chatClient The ChatClient instance to use
   *  @param tools Available tools (default: empty)
   *  @param messages Initial conversation history (default: empty)
   *  @return A new ReActAgent instance
   */
  def withClient(
    agentConfig: AgentConfig,
    chatClient: ChatClient,
    tools: List[ToolBase] = List.empty,
    messages: List[Message] = List.empty
  ): ReActAgent =
    new ReActAgent(agentConfig, tools, messages, chatClient)

  /**
   *  Create a new ReActAgent with a default ChatClient.
   *
   *  This is the standard way to create an agent. It automatically
   *  creates a ChatClient using the provided model configuration.
   *
   *  @param modelConfig Configuration for the LLM connection
   *  @param agentConfig Configuration for agent behavior
   *  @param tools Available tools (default: empty)
   *  @param messages Initial conversation history (default: empty)
   *  @return A new ReActAgent instance
   */
  def apply(
    modelConfig: ModelConfig,
    agentConfig: AgentConfig,
    tools: List[ToolBase] = List.empty,
    messages: List[Message] = List.empty
  ): ReActAgent =
    val chatClient = ChatClient(modelConfig)
    new ReActAgent(agentConfig, tools, messages, chatClient)
