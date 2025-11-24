package agent.core

import scala.util.Try

/**
 *  Base trait for agents that can interact with LLMs and use tools.
 *
 *  Agents manage conversation history, execute tools when requested by the LLM,
 *  and handle the complete request-response cycle including multi-turn interactions.
 */
trait Agent:
  /** Configuration for the agent's behavior */
  def config: AgentConfig

  /** Available tools that the agent can invoke */
  def tools: List[ToolBase]

  /** Complete conversation history including user, assistant, and tool messages */
  def messages: List[Message]

  /**
   *  Add a single message to the conversation history.
   *  @param message The message to add
   *  @return A new agent instance with the updated message history
   */
  def addMessage(message: Message): Agent

  /**
   *  Add multiple messages to the conversation history.
   * 
   *  @param newMessages The messages to add
   *  @return A new agent instance with the updated message history
   */
  def addMessages(newMessages: List[Message]): Agent

  /**
   *  Run the agent's reasoning loop until completion.
   *
   *  This method processes the user's message, invokes tools as needed,
   *  and continues the conversation until the LLM provides a final response
   *  or the maximum iteration limit is reached.
   *
   *  @param userMessage The user's input message
   *  @return Success with the final response, or Failure if an error occurs
   */
  def run(userMessage: String): Try[String]
