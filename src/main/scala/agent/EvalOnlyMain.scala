package agent

import agent.core.*
import agent.client.ModelConfig
import agent.tools.*

@main def runEvalOnly(): Unit =
  val modelConfig = ModelConfig.fromEnv()

  // Get all the base tools that will be available through eval
  val baseTools: List[ToolBase] = List(
    new CalculatorTool(),
    new WeatherTool(),
    new DateTimeTool(),
    new SearchTool()
  ) ++ MemoryTools.allTools

  // Generate the tool library documentation for the prompt
  val toolLibrary = EvalTool.generateToolLibrary(baseTools)

  val prompt =
    s"""You are a helpful AI assistant. You have access to an "eval" tool that can execute Scala 3 code.
       |
       |IMPORTANT: The eval tool is your ONLY way to interact with external capabilities.
       |All tool functionality must be accessed by writing Scala code that calls wrapper functions.
       |
       |AVAILABLE TOOL FUNCTIONS:
       |When you use the "eval" tool, the following Scala functions are available to call:
       |
       |```scala
       |$toolLibrary
       |```
       |
       |HOW TO USE TOOLS:
       |1. Write Scala 3 code that calls the appropriate function
       |2. The code will be evaluated and the result returned
       |3. Print the result using println() to see the output
       |
       |EXAMPLES:
       |
       |To calculate 5 + 3:
       |```scala
       |val result = calculator(operation = "add", a = 5.0, b = 3.0)
       |println(s"Result: $${result.result}")
       |```
       |
       |To get the weather:
       |```scala
       |val weather = get_weather(location = "San Francisco")
       |println(s"Temperature: $${weather.temperature}°$${weather.unit} - $${weather.condition}")
       |```
       |
       |COMPLEX OPERATIONS:
       |You can write multi-step Scala code to accomplish complex tasks:
       |
       |```scala
       |// Get weather and calculate temperature conversion
       |val weather = get_weather(location = "London", unit = Some("celsius"))
       |val fahrenheit = calculator(operation = "multiply", a = weather.temperature.toDouble, b = 9.0/5.0)
       |val converted = calculator(operation = "add", a = fahrenheit.result, b = 32.0)
       |println(s"$${weather.location}: $${weather.temperature}°C = $${converted.result}°F")
       |```
       |
       |GUIDELINES:
       |1. Always use the eval tool for any computation or external data
       |2. Write clear, readable Scala code
       |3. Use println() to output results you want to see
       |4. You can define variables, functions, and use Scala's full capabilities
       |5. Handle potential errors gracefully in your code
       |6. Store important user information in memory for future conversations
       |
       |Remember: You have a maximum of 10 iterations, so write efficient code that accomplishes tasks in fewer eval calls when possible.""".stripMargin

  val agentConfig = AgentConfig(
    systemPrompt = prompt,
    maxIterations = 10,
    stateFilePath = "agent_state.json"
  )

  // Only expose eval-related tools to the agent
  // The eval tool internally has access to all base tools via function calls
  val agentTools: List[ToolBase] = List(
    new EvalTool(baseTools, showLibraryTool = false)
  )

  val agent = ReActAgent(modelConfig, agentConfig, agentTools)

  val repl = new Repl(modelConfig, agentConfig, agentTools, agent)
  repl.run()
