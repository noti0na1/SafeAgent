package agent

import agent.core.*
import agent.client.{ChatClient, ModelConfig}
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

@main def runAgentRepl(): Unit =
  println("=" * 70)
  println("Scala 3 LLM Agent Framework - Interactive REPL")
  println("=" * 70)
  printHelp()
  println("=" * 70)
  println()

  val modelConfig = ModelConfig.fromEnv()

  var agentConfig = AgentConfig(
    systemPrompt = Some(
      """You are a helpful AI assistant with access to various tools.
        |When you need to perform calculations, get weather information, check the time,
        |or search for information, use the appropriate tools available to you.
        |Always explain your reasoning and the results clearly to the user.""".stripMargin
    ),
    maxIterations = 10,
    verbose = false
  )

  val tools: List[ToolBase] =
    val baseTools: List[ToolBase] = List(
      new CalculatorTool(),
      new WeatherTool(),
      new DateTimeTool(),
      new SearchTool()
    ) ++ MemoryTools.allTools
    baseTools :+ new EvalTool(baseTools)


  println(s"Initialized with model: ${modelConfig.model}")
  println(s"Available tools: ${tools.map(_.name).mkString(", ")}")
  // tools.foreach(
  //   tool => println(tool.toRawFunction)
  // )
  println()

  // Create initial agent
  var agent: Agent = ReActAgent(modelConfig, agentConfig, tools)
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
          agent = ReActAgent(modelConfig, agentConfig, tools)
          println("Conversation history cleared.")

        case ":verbose" =>
          agentConfig = agentConfig.copy(verbose = !agentConfig.verbose)
          agent = ReActAgent(modelConfig, agentConfig, tools)
          println(s"Verbose mode ${if agentConfig.verbose then "enabled" else "disabled"}.")

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

        case input if input.nonEmpty =>
          try
            agent.run(input) match
              case Success(response) =>
                println(s"\n$response\n")

              case Failure(ex) =>
                System.err.println(s"\nError: ${ex.getMessage}\n")
                ex.printStackTrace()
          catch
            case ex: Exception =>
              System.err.println(s"\nUnexpected error: ${ex.getMessage}\n")
              ex.printStackTrace()

        case _ => // Empty line, do nothing
    }
