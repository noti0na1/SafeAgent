package agent

import agent.core.*
import agent.client.{ChatClient, ModelConfig}
import agent.tools.*
import scala.util.{Try, Success, Failure, Random}
import utest.*

object SimpleTests extends TestSuite:

  def testModelConfig(): ModelConfig =
    ModelConfig.fromEnv()
    // ModelConfig(
    //   apiKey = ""
    //   baseUrl = ""
    //   model = ""
    // )

  val tests = Tests {
    test("calculatorExample - should use calculator tool for multiplication") {
      val modelConfig = testModelConfig()

      val agentConfig = AgentConfig(
        systemPrompt = "You are a helpful math assistant. Use the calculator tool for any calculations."
      )

      val tools: List[ToolBase] = List(new CalculatorTool())
      val agent = ReActAgent(modelConfig, agentConfig, tools)

      agent.run("What is 123 multiplied by 456?") match
        case Success(response) =>
          assert(response.nonEmpty)
          // Verify the result contains the expected calculation
          val normalized = response.replaceAll("[,\\s]", "")
          assert(normalized.contains("56088"))
        case Failure(ex) =>
          throw ex

      agent.run("Calculate 2 + (3 * 10) - 4 / 2") match
        case Success(response) =>
          assert(response.nonEmpty)
          val normalized = response.replaceAll("[,\\s]", "")
          assert(normalized.contains("30"))
        case Failure(ex) =>
          throw ex
    }

    test("multiToolExample - should handle multiple tools") {
      val modelConfig = testModelConfig()

      val agentConfig = AgentConfig(
        systemPrompt = "You are a helpful assistant with access to various tools."
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
          assert(response.nonEmpty)
          // Verify response mentions weather or time
          val lower = response.toLowerCase
          assert(lower.contains("weather") || lower.contains("time") || lower.contains("san francisco"))
        case Failure(ex) =>
          throw ex
    }

    test("conversationExample - should maintain context across messages") {
      val modelConfig = testModelConfig()

      val agentConfig = AgentConfig()

      var agent: Agent = ReActAgent(modelConfig, agentConfig, List(new CalculatorTool()))

      // First message
      val firstResult = agent.run("Calculate 15 times 8")
      firstResult match
        case Success(response) =>
          assert(response.nonEmpty)
          assert(response.contains("120"))
        case Failure(ex) =>
          throw ex

      // Second message with context
      val secondResult = agent.run("Now add 50 to that result")
      secondResult match
        case Success(response) =>
          assert(response.nonEmpty)
          // Should reference the previous result (120 + 50 = 170)
          assert(response.contains("170"))
        case Failure(ex) =>
          throw ex
    }

    test("customToolExample - ReverseTool should reverse strings") {
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

      val reverseTool = new ReverseTool()

      // Test the tool directly
      given State = State(AgentConfig())
      val result = reverseTool.invoke(ReverseInput("hello"))

      result match
        case Success(output) =>
          assert(output.reversed == "olleh")
        case Failure(ex) =>
          throw ex
    }

    test("unitTypeExample - RandomNumberTool should generate random numbers") {
      case class RandomOutput(value: Double) derives upickle.default.ReadWriter
      given ToolDataType[RandomOutput] = ToolDataType.derived[RandomOutput]

      class RandomNumberTool extends Tool[Unit, RandomOutput]:
        override val name = "random_number"
        override val description = "Generates a random number between 0 and 1. No arguments required."

        override def invoke(input: Unit)(using state: State): Try[RandomOutput] =
          Success(RandomOutput(value = Random.nextDouble()))

      val randomTool = new RandomNumberTool()

      // Test the tool directly
      given State = State(AgentConfig())
      val result = randomTool.invoke(())

      result match
        case Success(output) =>
          assert(output.value >= 0.0 && output.value <= 1.0)
        case Failure(ex) =>
          throw ex
    }

    test("unitTypeExample - LogTool should log messages and return Unit") {
      case class LogInput(message: String) derives upickle.default.ReadWriter
      given ToolDataType[LogInput] = ToolDataType.derived[LogInput]

      class LogTool extends Tool[LogInput, Unit]:
        override val name = "log_message"
        override val description = "Logs a message to the console. Returns nothing."

        override def invoke(input: LogInput)(using state: State): Try[Unit] =
          Success(())

      val logTool = new LogTool()

      // Test the tool directly
      given State = State(AgentConfig())
      val result = logTool.invoke(LogInput("test message"))

      result match
        case Success(_) =>
        case Failure(ex) =>
          throw ex
    }

    test("unitTypeExample - Tools should have valid schemas") {
      case class RandomOutput(value: Double) derives upickle.default.ReadWriter
      given ToolDataType[RandomOutput] = ToolDataType.derived[RandomOutput]

      class RandomNumberTool extends Tool[Unit, RandomOutput]:
        override val name = "random_number"
        override val description = "Generates a random number between 0 and 1. No arguments required."

        override def invoke(input: Unit)(using state: State): Try[RandomOutput] =
          Success(RandomOutput(value = Random.nextDouble()))

      case class LogInput(message: String) derives upickle.default.ReadWriter
      given ToolDataType[LogInput] = ToolDataType.derived[LogInput]

      class LogTool extends Tool[LogInput, Unit]:
        override val name = "log_message"
        override val description = "Logs a message to the console. Returns nothing."

        override def invoke(input: LogInput)(using state: State): Try[Unit] =
          Success(())

      val randomTool = new RandomNumberTool()
      val logTool = new LogTool()

      // Verify schemas can be generated
      val randomSchema = randomTool.toRawFunction
      val logSchema = logTool.toRawFunction

      // Access the function name from the nested Map structure
      val randomFunctionMap = randomSchema("function").asInstanceOf[Map[String, Any]]
      val logFunctionMap = logSchema("function").asInstanceOf[Map[String, Any]]

      assert(randomFunctionMap("name") == "random_number")
      assert(logFunctionMap("name") == "log_message")
    }

    test("memoryTools - should store and retrieve values") {
      given state: State = State(AgentConfig())

      val storeTool = new StoreMemoryTool()
      val retrieveTool = new RetrieveMemoryTool()

      // Store a value
      val storeResult = storeTool.invoke(StoreMemoryInput("test_key", "test_value"))
      storeResult match
        case Success(_) => // Success
        case Failure(ex) => throw ex

      // Retrieve the value
      val retrieveResult = retrieveTool.invoke(RetrieveMemoryInput("test_key"))
      retrieveResult match
        case Success(output) =>
          assert(output.found)
          assert(output.key == "test_key")
          assert(output.value == Some("test_value"))
        case Failure(ex) =>
          throw ex
    }

    test("memoryTools - should return not found for missing keys") {
      given state: State = State(AgentConfig())

      val retrieveTool = new RetrieveMemoryTool()

      // Try to retrieve a non-existent key
      val result = retrieveTool.invoke(RetrieveMemoryInput("nonexistent_key"))
      result match
        case Success(output) =>
          assert(!output.found)
          assert(output.key == "nonexistent_key")
          assert(output.value.isEmpty)
        case Failure(ex) =>
          throw ex
    }

    test("memoryTools - should list stored keys") {
      given state: State = State(AgentConfig())

      val storeTool = new StoreMemoryTool()
      val listTool = new ListMemoryTool()

      // Store multiple values
      storeTool.invoke(StoreMemoryInput("key1", "value1"))
      storeTool.invoke(StoreMemoryInput("key2", "value2"))
      storeTool.invoke(StoreMemoryInput("key3", "value3"))

      // List all keys
      val result = listTool.invoke(())
      result match
        case Success(output) =>
          assert(output.count == 3)
          assert(output.keys.sorted == List("key1", "key2", "key3"))
        case Failure(ex) =>
          throw ex
    }

    test("memoryTools - should list empty keys when no memory stored") {
      given state: State = State(AgentConfig())

      val listTool = new ListMemoryTool()

      // List keys with no stored values
      val result = listTool.invoke(())
      result match
        case Success(output) =>
          assert(output.count == 0)
          assert(output.keys.isEmpty)
        case Failure(ex) =>
          throw ex
    }

    test("memoryTools - should update existing keys") {
      given state: State = State(AgentConfig())

      val storeTool = new StoreMemoryTool()
      val retrieveTool = new RetrieveMemoryTool()

      // Store initial value
      storeTool.invoke(StoreMemoryInput("key", "initial_value"))

      // Update the value
      storeTool.invoke(StoreMemoryInput("key", "updated_value"))

      // Retrieve the updated value
      val result = retrieveTool.invoke(RetrieveMemoryInput("key"))
      result match
        case Success(output) =>
          assert(output.found)
          assert(output.value == Some("updated_value"))
        case Failure(ex) =>
          throw ex
    }

    test("memoryTools - should persist across tool invocations with same state") {
      given state: State = State(AgentConfig())

      val storeTool = new StoreMemoryTool()
      val retrieveTool = new RetrieveMemoryTool()
      val listTool = new ListMemoryTool()

      // Store values
      storeTool.invoke(StoreMemoryInput("user_name", "Alice"))
      storeTool.invoke(StoreMemoryInput("user_age", "30"))

      // Retrieve values
      val nameResult = retrieveTool.invoke(RetrieveMemoryInput("user_name"))
      val ageResult = retrieveTool.invoke(RetrieveMemoryInput("user_age"))
      val listResult = listTool.invoke(())

      nameResult match
        case Success(output) => assert(output.value == Some("Alice"))
        case Failure(ex) => throw ex

      ageResult match
        case Success(output) => assert(output.value == Some("30"))
        case Failure(ex) => throw ex

      listResult match
        case Success(output) => assert(output.count == 2)
        case Failure(ex) => throw ex
    }

    test("memoryTools - should work with agent") {
      val modelConfig = testModelConfig()

      val agentConfig = AgentConfig(
        systemPrompt = "You are a helpful assistant with memory capabilities. Use memory tools to remember information."
      )

      val tools: List[ToolBase] = MemoryTools.allTools
      val agent = ReActAgent(modelConfig, agentConfig, tools)

      // First interaction: store information
      agent.run("Remember that my favorite color is blue") match
        case Success(response) =>
          assert(response.nonEmpty)
        case Failure(ex) =>
          throw ex

      // Second interaction: retrieve information
      agent.run("What is my favorite color?") match
        case Success(response) =>
          assert(response.nonEmpty)
          val lower = response.toLowerCase
          assert(lower.contains("blue"))
        case Failure(ex) =>
          throw ex
    }
  }
