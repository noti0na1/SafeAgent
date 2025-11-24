package agent

import agent.core.*
import agent.client.{ChatClient, ModelConfig}
import agent.tools.*
import scala.util.{Success, Failure}

/** Example programs demonstrating the agent framework */
object Examples:

  /** Simple example with calculator tool */
  def calculatorExample(): Unit =
    println("=== Calculator Example ===\n")

    val modelConfig = ModelConfig.fromEnv()

    val agentConfig = AgentConfig(
      systemPrompt = Some("You are a helpful math assistant. Use the calculator tool for any calculations.")
    )

    val tools: List[ToolBase] = List(new CalculatorTool())
    val agent = ReActAgent(modelConfig, agentConfig, tools)

    agent.run("What is 123 multiplied by 456?") match
      case Success(response) =>
        println(s"Agent: $response")
      case Failure(ex) =>
        println(s"Error: ${ex.getMessage}")

  /** Example with multiple tools */
  def multiToolExample(): Unit =
    println("\n=== Multi-Tool Example ===\n")

    val modelConfig = ModelConfig.fromEnv()

    val agentConfig = AgentConfig(
      systemPrompt = Some("You are a helpful assistant with access to various tools.")
    )

    val tools: List[ToolBase] = List(
      new CalculatorTool(),
      new WeatherTool(),
      new DateTimeTool(),
      new SearchTool()
    )

    val agent = ReActAgent(modelConfig, agentConfig, tools)

    agent.run("What's the weather in San Francisco and what time is it there?") match
      case Success(response) =>
        println(s"Agent: $response")
      case Failure(ex) =>
        println(s"Error: ${ex.getMessage}")

  /** Example of conversation with context */
  def conversationExample(): Unit =
    println("\n=== Conversation Example ===\n")

    val modelConfig = ModelConfig.fromEnv()

    val agentConfig = AgentConfig()

    var agent: Agent = ReActAgent(modelConfig, agentConfig, List(new CalculatorTool()))

    // First message
    agent.run("Calculate 15 times 8") match
      case Success(response) =>
        println(s"Agent: $response\n")
      case Failure(ex) =>
        println(s"Error: ${ex.getMessage}")

    // Second message with context
    agent.run("Now add 50 to that result") match
      case Success(response) =>
        println(s"Agent: $response")
      case Failure(ex) =>
        println(s"Error: ${ex.getMessage}")

  /** Custom tool example */
  def customToolExample(): Unit =
    println("\n=== Custom Tool Example ===\n")

    // Define input and output types
    case class ReverseInput(text: String) derives upickle.default.ReadWriter
    given ToolDataType[ReverseInput] = ToolDataType.derived[ReverseInput]

    case class ReverseOutput(reversed: String) derives upickle.default.ReadWriter
    given ToolDataType[ReverseOutput] = ToolDataType.derived[ReverseOutput]

    // Define a custom tool
    class ReverseTool extends Tool[ReverseInput, ReverseOutput]:
      override val name = "reverse_string"
      override val description = "Reverses a string"

      override def invoke(input: ReverseInput)(using state: State): scala.util.Try[ReverseOutput] =
        scala.util.Success(ReverseOutput(reversed = input.text.reverse))

  /** Example demonstrating Unit type handling - tools with no input or no output */
  def unitTypeExample(): Unit =
    println("\n=== Unit Type Example ===\n")

    // Tool with no input (Unit input) - generates a random number
    case class RandomOutput(value: Double) derives upickle.default.ReadWriter
    given ToolDataType[RandomOutput] = ToolDataType.derived[RandomOutput]

    class RandomNumberTool extends Tool[Unit, RandomOutput]:
      override val name = "random_number"
      override val description = "Generates a random number between 0 and 1. No arguments required."

      override def invoke(input: Unit)(using state: State): scala.util.Try[RandomOutput] =
        scala.util.Success(RandomOutput(value = scala.util.Random.nextDouble()))

    // Tool with no output (Unit output) - logs a message
    case class LogInput(message: String) derives upickle.default.ReadWriter
    given ToolDataType[LogInput] = ToolDataType.derived[LogInput]

    class LogTool extends Tool[LogInput, Unit]:
      override val name = "log_message"
      override val description = "Logs a message to the console. Returns nothing."

      override def invoke(input: LogInput)(using state: State): scala.util.Try[Unit] =
        println(s"[LOG] ${input.message}")
        scala.util.Success(())

    val randomTool = new RandomNumberTool()
    val logTool = new LogTool()

    println("Random Number Tool Schema:")
    println(randomTool.toRawFunction)
    println("\nLog Tool Schema:")
    println(logTool.toRawFunction)
    println()

    val modelConfig = ModelConfig.fromEnv()

    val agentConfig = AgentConfig(
      systemPrompt = Some("You can generate random numbers with random_number (no arguments needed) and log messages with log_message.")
    )

    val agent = ReActAgent(modelConfig, agentConfig, List(randomTool, logTool))

    agent.run("Generate a random number and log it with a message.") match
      case Success(response) =>
        println(s"Agent: $response")
      case Failure(ex) =>
        println(s"Error: ${ex.getMessage}")

@main def runExamples(): Unit =
  println("Running Agent Framework Examples\n")
  println("Note: Set OPENAI_API_KEY environment variable to run these examples")
  println("=" * 70)

  // Uncomment to run examples (requires valid API key)
  // Examples.calculatorExample()
  // Examples.multiToolExample()
  // Examples.conversationExample()
  // Examples.unitTypeExample()

  println("\nTo run examples, uncomment the example calls in Examples.scala")
  println("and ensure OPENAI_API_KEY is set in your environment.")
