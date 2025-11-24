package agent.core

/**
 *  Configuration for agent execution behavior.
 *
 *  @param systemPrompt System message to guide the agent's behavior
 *  @param maxIterations Maximum number of tool-calling iterations before giving up (default: 10)
 *  @param verbose Whether to print detailed tool execution logs (default: false)
 */
case class AgentConfig(
  systemPrompt: String = "You are a helpful AI agent that can use tools to assist with user requests.",
  maxIterations: Int = 10
)
