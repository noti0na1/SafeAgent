# SafeAgent

A type-safe, extensible LLM agent framework built with Scala 3 that implements the ReAct (Reasoning and Acting) pattern. SafeAgent enables you to build intelligent agents that can reason about tasks, dynamically invoke tools, and execute complex workflows with full type safety.

## Features

- **ReAct Pattern Implementation**: Iterative reasoning loop where agents think, act, and observe
- **Type-Safe Tool System**: Automatic JSON schema generation from Scala case classes
- **OpenAI Integration**: Compatible with OpenAI and OpenAI-compatible LLM APIs
- **Interactive REPL**: Command-line interface for real-time agent interaction
- **Extensible Architecture**: Easy-to-add custom tools with compile-time verification
- **State Management**: Scoped storage for maintaining context across conversations
- **Code Evaluation**: Execute Scala code dynamically with tool access
- **Built-in Tools**: Calculator, weather, date/time, search, memory, and code evaluation

## Quick Start

### Prerequisites

- Java 11 or higher
- SBT (Scala Build Tool)
- OpenAI API key or compatible LLM endpoint

### Installation

1. Clone the repository:
```bash
git clone https://github.com/noti0na1/SafeAgent.git
cd SafeAgent
```

2. Set up your OpenAI credentials:
```bash
export OPENAI_API_KEY="your-api-key-here"
export OPENAI_BASE_URL="https://api.openai.com/v1"  # Optional
export OPENAI_MODEL="gpt-4"  # Optional, defaults to gpt-4
```

3. Run the interactive REPL:
```bash
sbt run
```

### Basic Usage

Once in the REPL, you can interact with the agent naturally:

```
> What is 15 * 24 + 100?

Agent: Let me calculate that for you.
[Tool: calculator(operation=multiply, a=15, b=24)]
[Tool: calculator(operation=add, a=360, b=100)]
Result: 460

> What's the weather in Lausanne?

Agent: [Tool: weather(location=Lausanne, unit=celsius)]
The current weather in Lausanne is 8°C and sunny.

> Store "meeting" with value "3pm tomorrow"

Agent: [Tool: store_memory(key=meeting, value=3pm tomorrow)]
I've stored that information.

> What did I ask you to remember?

Agent: [Tool: retrieve_memory(key=meeting)]
You asked me to remember: meeting at 3pm tomorrow.
```

### REPL Commands

- `:help` - Show available commands
- `:tools` - List all available tools
- `:messages` - Display conversation history
- `:clear` - Reset the conversation
- `:verbose` - Toggle debug output for tool calls
- `:exit` or `:quit` - Exit the REPL

## Architecture

### Core Components

#### Agent System

- **[Agent.scala](src/main/scala/agent/core/Agent.scala)** - Base trait defining agent interface
- **[ReActAgent.scala](src/main/scala/agent/core/ReActAgent.scala)** - ReAct pattern implementation with iterative reasoning loop
- **[AgentConfig.scala](src/main/scala/agent/core/AgentConfig.scala)** - Configuration for system prompts and behavior

#### Tool System

- **[Tool.scala](src/main/scala/agent/core/Tool.scala)** - Generic tool framework with automatic schema generation
- **[ToolDataType.scala](src/main/scala/agent/core/Tool.scala)** - JSON schema and serialization support
- **[ToolServer.scala](src/main/scala/agent/tools/ToolServer.scala)** - HTTP server for inter-process tool communication

#### Message & State

- **[Message.scala](src/main/scala/agent/core/Message.scala)** - Message types and conversation history
- **[State.scala](src/main/scala/agent/core/State.scala)** - Scoped key-value storage for agent context

#### LLM Integration

- **[ChatClient.scala](src/main/scala/agent/client/ChatClient.scala)** - OpenAI SDK wrapper for LLM communication
- **[ModelConfig.scala](src/main/scala/agent/client/ModelConfig.scala)** - Model configuration and credentials

### Built-in Tools

| Tool | Description | Example |
|------|-------------|---------|
| **calculator** | Perform arithmetic operations | `calculator(operation=add, a=5, b=3)` |
| **weather** | Get weather information (mock) | `weather(location=London, unit=celsius)` |
| **datetime** | Get current date/time | `datetime(timezone=UTC, format=iso)` |
| **search** | Web search (mock) | `search(query=Scala 3, limit=5)` |
| **store_memory** | Store key-value pairs | `store_memory(key=name, value=Alice)` |
| **retrieve_memory** | Retrieve stored values | `retrieve_memory(key=name)` |
| **list_memory** | List all stored keys | `list_memory()` |
| **eval** | Execute Scala code | `eval(code="List(1,2,3).sum")` |

## Creating Custom Tools

Creating a custom tool is straightforward with Scala 3's type system:

```scala
import agent.core.{Tool, ToolDataType, State}
import upickle.default.*

// Define input and output types
case class TranslateInput(
  text: String,
  targetLanguage: String
) derives ReadWriter, ToolDataType

case class TranslateOutput(
  translatedText: String,
  confidence: Double
) derives ReadWriter, ToolDataType

// Implement the tool
class TranslateTool extends Tool[TranslateInput, TranslateOutput]:
  override val name = "translate"
  override val description = "Translate text to target language"

  override def run(input: TranslateInput)(using state: State): TranslateOutput =
    // Your translation logic here
    TranslateOutput(
      translatedText = s"[${input.targetLanguage}] ${input.text}",
      confidence = 0.95
    )

// Use the tool
val agent = ReActAgent(
  client = chatClient,
  tools = List(TranslateTool()),
  config = AgentConfig(maxIterations = 10)
)
```

The `derives` mechanism automatically:
- Generates JSON serialization/deserialization
- Creates OpenAI-compatible function schemas
- Provides type-safe validation

## Examples

Run the examples to see various usage patterns:

```bash
sbt "runMain agent.Examples"
```

The examples demonstrate:

1. **Basic Calculator** - Simple arithmetic operations
2. **Multi-Tool Workflow** - Combining multiple tools in one task
3. **Conversation Context** - Multi-turn conversations with memory
4. **Custom Tool Definition** - Creating and using custom tools
5. **Unit Type Handling** - Tools without parameters or return values

## Configuration

### Environment Variables

- `OPENAI_API_KEY` - Your OpenAI API key (required)
- `OPENAI_BASE_URL` - API base URL (default: `https://api.openai.com/v1`)
- `OPENAI_MODEL` - Model identifier (default: `gpt-4`)

### Agent Configuration

Configure agent behavior programmatically:

```scala
val config = AgentConfig(
  systemPrompt = "You are a helpful assistant specialized in...",
  maxIterations = 15,
  verbose = true
)

val agent = ReActAgent(client, tools, config)
```

### Model Configuration

```scala
val modelConfig = ModelConfig(
  apiKey = "your-api-key",
  baseUrl = "https://api.openai.com/v1",
  model = "gpt-4",
  maxTokens = Some(2000),
  temperature = Some(0.7)
)

val client = ChatClient(modelConfig)
```

## How It Works

### ReAct Loop

The ReAct pattern implements a reasoning loop:

1. **Reason**: Agent receives input and current conversation history
2. **Act**: LLM decides to call tools or respond directly
3. **Observe**: Tool results are added to conversation history
4. **Iterate**: Loop continues until final response or max iterations

```
User Input → Add to History → Call LLM
                                  ↓
                            Has Tool Calls?
                            ├─ Yes → Execute Tools → Add Results → Loop
                            └─ No → Return Final Response
```

### Type-Safe Tool Invocation

Tools are type-checked at compile time:

```scala
// Define tool with typed input/output
class MyTool extends Tool[MyInput, MyOutput]:
  def run(input: MyInput)(using State): MyOutput = ...

// Schema is automatically generated
// LLM calls tool with JSON
// Input is validated and deserialized
// Output is serialized back to LLM
```

## Advanced Features

### State Management

Maintain context across tool invocations:

```scala
def run(input: MyInput)(using state: State): MyOutput =
  val previousValue = state.get("key").getOrElse("default")
  state.set("key", "newValue")
  // ... rest of implementation
```

### Code Evaluation

The EvalTool allows the agent to execute Scala code:

```scala
> Calculate the factorial of 10 using Scala code

Agent: [Tool: eval(code="(1 to 10).product")]
Result: 3628800
```

The evaluated code can call other tools via the injected `callTool` function.

### Custom System Prompts

Specialize your agent for specific domains:

```scala
val config = AgentConfig(
  systemPrompt = """You are a financial advisor assistant.
    Always consider risk tolerance and diversification.
    Use the calculator for precise calculations."""
)
```

## Development

### Project Structure

```
SafeAgent/
├── build.sbt                      # Build configuration
├── src/main/scala/agent/
│   ├── Main.scala                 # REPL entry point
│   ├── Examples.scala             # Usage examples
│   ├── core/                      # Core framework
│   │   ├── Agent.scala
│   │   ├── ReActAgent.scala
│   │   ├── Tool.scala
│   │   ├── Message.scala
│   │   ├── State.scala
│   │   └── AgentConfig.scala
│   ├── client/                    # LLM integration
│   │   ├── ChatClient.scala
│   │   └── ModelConfig.scala
│   └── tools/                     # Built-in tools
│       ├── CalculatorTool.scala
│       ├── WeatherTool.scala
│       ├── DateTimeTool.scala
│       ├── SearchTool.scala
│       ├── MemoryTools.scala
│       ├── EvalTool.scala
│       └── ToolServer.scala
└── library/
    └── ToolClient.scala           # Tool client library
```

### Building

```bash
# Compile
sbt compile

# Run tests (if available)
sbt test

# Create distributable package
sbt package

# Run with specific main class
sbt run
```

### Contributing

Contributions are welcome! Areas for enhancement:

- Additional built-in tools (file operations, HTTP requests, etc.)
- Streaming responses support
- Multi-agent collaboration
- Tool result caching
- Async tool execution
- Enhanced error recovery strategies

## License

[Specify your license here]

## Acknowledgments

- Built with Scala 3 and the OpenAI API
- Inspired by the ReAct pattern from [Yao et al., 2023](https://arxiv.org/abs/2210.03629)
- Uses [upickle](https://github.com/com-lihaoyi/upickle) for JSON serialization

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Check existing documentation in [src/main/scala/agent/](src/main/scala/agent/)
- Review examples in [Examples.scala](src/main/scala/agent/Examples.scala)
