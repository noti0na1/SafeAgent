package agent.core

/**
 *  Configuration for agent execution behavior.
 *
 *  @param systemPrompt Optional system message to guide the agent's behavior
 *  @param maxIterations Maximum number of tool-calling iterations before giving up (default: 10)
 *  @param verbose Whether to print detailed execution logs (default: false)
 */
case class AgentConfig(
  systemPrompt: Option[String] = None,
  maxIterations: Int = 10,
  verbose: Boolean = false
)
