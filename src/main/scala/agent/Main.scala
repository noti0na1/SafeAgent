package agent

import agent.core.*
import agent.client.ModelConfig
import agent.tools.*
import scala.util.{Success, Failure}

def printHelp(): Unit =
  println("\nCommands:")
  println("  :exit or :quit - Exit the REPL")
  println("  :tools - List available tools")
  println("  :clear - Clear conversation history")
  println("  :verbose - Toggle verbose mode (show tool calls)")
  println("  :messages - Print all conversation messages")
  println("  :help - Show this help message")
  println()

@main def run(): Unit =
  println("=" * 70)
  println("Scala 3 Safe Agent Framework - Interactive REPL")
  println("=" * 70)
  printHelp()
  println("=" * 70)
  println()

  val modelConfig = ModelConfig.fromEnv()
  val stateFilePath = "agent_state.json"

  var agentConfig = AgentConfig(
    systemPrompt =
      """You are a helpful AI assistant with access to powerful tools that extend your capabilities.
        |
        |IMPORTANT GUIDELINES FOR TOOL USAGE:
        |
        |1. ALWAYS use tools when appropriate - don't try to guess or approximate results
        |   - For ANY calculation (even simple math): use "calculator"
        |   - For current date/time information: use "get_datetime"
        |   - For weather queries: use "get_weather"
        |   - For factual information or research: use "search"
        |   - For remembering information across conversations: use "store_memory", "retrieve_memory", "list_memory"
        |   - For evaluating complex multi-step Scala operations: use "eval"
        |
        |2. MEMORY TOOLS - Use aggressively and proactively:
        |   *** CRITICAL: At the START of EVERY conversation, use "list_memory" to check what you already know ***
        |
        |   STORE information when the user:
        |   - Shares personal details (name, preferences, location, job, interests, etc.)
        |   - Mentions important dates, events, or deadlines
        |   - Provides configuration details, API keys, or system information
        |   - States goals, plans, or ongoing projects
        |   - Gives feedback or corrections (store these as learned preferences)
        |   - References previous conversations or context
        |
        |   RECALL information by:
        |   - Using "list_memory" at the beginning of each conversation to load context
        |   - Using "retrieve_memory" when the user references something from before
        |   - Checking memories before asking questions the user may have already answered
        |
        |   Think of memory as your persistent knowledge base - use it liberally!
        |   When in doubt, store it. Better to have information and not need it than need it and not have it.
        |
        |3. Be proactive with tools:
        |   - If a question requires real-time data, use a tool instead of relying on training data
        |   - If a user asks "what's the weather", use "get_weather" - don't ask for location unless needed
        |   - Don't ask questions that might already be answered in your memory - check first!
        |
        |4. Tool usage workflow:
        |   - FIRST: Check memories with "list_memory" or "retrieve_memory"
        |   - THEN: Think step-by-step about what other tools you need
        |   - Use multiple tools in sequence if needed to answer complex queries
        |   - Always verify tool results before presenting them to the user
        |   - Explain what each tool does when you use it
        |
        |5. Be transparent:
        |   - Tell the user which tool you're using and why
        |   - If a tool call fails, explain the error and try an alternative approach
        |   - Show your reasoning process
        |
        |6. Remember: You have a maximum of 10 iterations to solve a problem, so plan your tool usage efficiently.
        |
        |Your goal is to provide accurate, tool-assisted responses that leverage these capabilities to their fullest extent.
        |Build up a rich memory over time to provide increasingly personalized and context-aware assistance.""".stripMargin,
    maxIterations = 10
  )

  val tools: List[ToolBase] =
    val baseTools: List[ToolBase] = List(
      new CalculatorTool(),
      new WeatherTool(),
      new DateTimeTool(),
      new SearchTool()
    ) ++ MemoryTools.allTools
    baseTools :+ new GetToolLibraryTool(baseTools) :+ new EvalTool(baseTools)


  println(s"Initialized with model: ${modelConfig.model}")
  println(s"Available tools: ${tools.map(_.name).mkString(", ")}")
  // tools.foreach(
  //   tool => println(tool.toRawFunction)
  // )
  println()

  // Create state and load persistent data from file
  val state = new State()

  // Get all persistent keys from tools (like MemoryTools.memoryKey)
  val persistentKeys = List(MemoryTools.memoryKey)

  state.loadFromFile(stateFilePath, persistentKeys) match
    case Success(_) =>
      println(s"Loaded persistent state from $stateFilePath")
    case Failure(ex) =>
      System.err.println(s"Warning: Could not load state from $stateFilePath: ${ex.getMessage}")

  // Create initial agent with the state
  var agent: ReActAgent = ReActAgent(modelConfig, agentConfig, tools, state = state)

  // Add shutdown hook to save state when exiting
  sys.addShutdownHook {
    state.saveToFile(stateFilePath) match
      case Success(_) =>
        println(s"\nSaved persistent state to $stateFilePath")
      case Failure(ex) =>
        System.err.println(s"\nWarning: Could not save state to $stateFilePath: ${ex.getMessage}")
  }

  // REPL loop
  Iterator.continually(scala.io.StdIn.readLine("> "))
    .takeWhile(_ != null)
    .map(_.trim)
    .foreach { line =>
      line match
        case ":exit" | ":quit" =>
          println("Goodbye!")
          sys.exit(0)

        case ":tools" =>
          println("\nAvailable Tools:")
          tools.foreach { tool =>
            print(tool.getFullDescription)
          }
          println()

        case ":clear" =>
          agent = ReActAgent(modelConfig, agentConfig, tools, state = agent.state)
          println("Conversation history cleared.")

        case ":verbose" =>
          agent.state.verbose = !agent.state.verbose
          println(s"Verbose mode ${if agent.state.verbose then "enabled" else "disabled"}.")

        case ":messages" =>
          val msgs = agent.messages
          if msgs.isEmpty then
            println("\nNo messages in conversation history.")
          else
            println("\n" + "=" * 70)
            println(s"Conversation History (${msgs.length} messages)")
            println("=" * 70)
            msgs.zipWithIndex.foreach { case (msg, idx) =>
              println(s"\n[Message ${idx + 1}]")
              print(msg.toFormattedString())
            }
            println("=" * 70 + "\n")

        case ":help" =>
          printHelp()

        case input if input.startsWith(":") =>
          println(s"Unknown command: $input")

        case input if input.nonEmpty =>
          agent.run(input) match
            case Success(response) =>
              println(s"\n$response\n")

            case Failure(ex) =>
              System.err.println(s"\nError: ${ex.getMessage}\n")
              ex.printStackTrace()

        case _ => // Empty line, do nothing
    }
