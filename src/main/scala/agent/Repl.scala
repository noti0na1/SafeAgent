package agent

import agent.core.*
import agent.client.ModelConfig
import agent.tools.*
import scala.util.{Success, Failure}
import scala.compiletime.uninitialized

class Repl(
    modelConfig: ModelConfig,
    agentConfig: AgentConfig,
    tools: List[ToolBase],
    agent: Agent
):
  private val persistentKeys = List(MemoryTools.memoryKey)
  private val stateFilePath = agentConfig.stateFilePath

  def printHelp(): Unit =
    println("\nCommands:")
    println("  :exit or :quit - Exit the REPL")
    println("  :tools - List available tools")
    println("  :clear - Clear conversation history")
    println("  :verbose - Toggle verbose mode (show tool calls)")
    println("  :messages - Print all conversation messages")
    println("  :help - Show this help message")
    println()

  def initializeState(): Unit =
    agent.state.loadFromFile(stateFilePath, persistentKeys) match
      case Success(_) =>
        println(s"Loaded persistent state from $stateFilePath")
      case Failure(ex) =>
        System.err.println(s"Warning: Could not load state from $stateFilePath: ${ex.getMessage}")

  def addStatePersistenceOnExit(): Unit =
    sys.addShutdownHook {
      agent.state.saveToFile(stateFilePath) match
        case Success(_) =>
          println(s"\nSaved persistent state to $stateFilePath")
        case Failure(ex) =>
          System.err.println(s"\nWarning: Could not save state to $stateFilePath: ${ex.getMessage}")
    }

  def initialize(): Unit =
    initializeState()
    addStatePersistenceOnExit()

  def printBanner(): Unit =
    println("=" * 70)
    println("Scala 3 Safe Agent Framework - Interactive REPL")
    println("=" * 70)
    printHelp()
    println("=" * 70)
    println()
    println(s"Initialized with model: ${modelConfig.model}")
    println(s"Available tools: ${tools.map(_.name).mkString(", ")}")
    println()

  def handleCommand(line: String): Boolean =
    line match
      case ":exit" | ":quit" =>
        println("Goodbye!")
        false

      case ":tools" =>
        println("\nAvailable Tools:")
        tools.foreach { tool =>
          print(tool.getFullDescription)
        }
        println()
        true

      case ":clear" =>
        agent.reset()
        initializeState()
        println("Conversation history cleared.")
        true

      case ":verbose" =>
        agent.state.verbose = !agent.state.verbose
        println(s"Verbose mode ${if agent.state.verbose then "enabled" else "disabled"}.")
        true

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
        true

      case ":help" =>
        printHelp()
        true

      case input if input.startsWith(":") =>
        println(s"Unknown command: $input")
        true

      case input if input.nonEmpty =>
        agent.run(input) match
          case Success(response) =>
            println(s"\n$response\n")
          case Failure(ex) =>
            System.err.println(s"\nError: ${ex.getMessage}\n")
            ex.printStackTrace()
        true

      case _ => // Empty line, do nothing
        true

  def run(): Unit =
    initialize()
    printBanner()

    Iterator.continually(scala.io.StdIn.readLine("> "))
      .takeWhile(_ != null)
      .map(_.trim)
      .takeWhile(handleCommand)
      .foreach(_ => ()) // consume the iterator
