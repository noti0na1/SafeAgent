# SafeAgent

A type-safe LLM agent framework built with Scala 3 implementing the ReAct (Reasoning and Acting) pattern. Build intelligent agents that reason about tasks, invoke tools dynamically, and execute complex workflows with compile-time type safety.

## Features

- **ReAct Pattern**: Iterative reasoning loop (think → act → observe)
- **Type-Safe Tools**: Automatic JSON schema generation with compile-time verification
- **OpenAI Compatible**: Works with OpenAI and compatible LLM APIs
- **State Persistence**: Automatic save/load with scoped storage
- **Interactive REPL**: Command-line interface with enhanced system prompts
- **Built-in Tools**: Calculator, weather, date/time, search, memory, code evaluation
- **Extensible**: Add custom tools with full type safety

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

```
> What is 15 * 24 + 100?
**15 * 24 + 100 = 460**

Here's the breakdown:
- 15 × 24 = 360
- 360 + 100 = 460

> Remember I have a meeting at 3 PM tomorrow.
Done! I've stored that you have a meeting at 3 PM tomorrow. I'll remember this for future reference. If you need any reminders or want to add more details about the meeting, just let me know!

> How is the weather of where I live?
Let me check my memory to see if you've told me your location before.
I see there's a "user_location" key in my memory! Let me retrieve that:
Great! Now let me get the weather for Lausanne:
Perfect! Here's the weather in Lausanne:
- **Temperature:** 72°F (approximately 22°C)
- **Condition:** Sunny

Looks like nice weather today! 😊
```

**REPL Commands**: `:help`, `:tools`, `:messages`, `:clear`, `:verbose`, `:exit`

## Architecture

### Core Components

- **Agent**: [Agent.scala](src/main/scala/agent/core/Agent.scala), [ReActAgent.scala](src/main/scala/agent/core/ReActAgent.scala), [AgentConfig.scala](src/main/scala/agent/core/AgentConfig.scala)
- **Tools**: [Tool.scala](src/main/scala/agent/core/Tool.scala), [ToolServer.scala](src/main/scala/agent/tools/ToolServer.scala)
- **State**: [Message.scala](src/main/scala/agent/core/Message.scala), [State.scala](src/main/scala/agent/core/State.scala)
- **LLM**: [ChatClient.scala](src/main/scala/agent/client/ChatClient.scala), [ModelConfig.scala](src/main/scala/agent/client/ModelConfig.scala)

### Some Built-in Tools

| Tool | Description |
|------|-------------|
| `calculator` | Arithmetic operations |
| `weather` | Weather information (mock) |
| `datetime` | Current date/time |
| `search` | Web search (mock) |
| `store_memory` / `retrieve_memory` / `list_memory` | Persistent memory storage |
| `eval` / `get_tool_library` | Execute Scala code in a separate process with full tool access |

## Creating Custom Tools

```scala
case class TranslateInput(text: String, targetLanguage: String)
  derives ReadWriter, ToolDataType

case class TranslateOutput(translatedText: String, confidence: Double)
  derives ReadWriter, ToolDataType

class TranslateTool extends Tool[TranslateInput, TranslateOutput]:
  val name = "translate"
  val description = "Translate text to target language"

  def invoke(input: TranslateInput)(using State): Try[TranslateOutput] =
    Success(TranslateOutput(s"[${input.targetLanguage}] ${input.text}", 0.95))
```

The `derives` mechanism provides automatic JSON serialization and OpenAI-compatible schema generation.

## Configuration

**Environment Variables**:
- `OPENAI_API_KEY` (required)
- `OPENAI_BASE_URL` (default: `https://api.openai.com/v1`)
- `OPENAI_MODEL` (default: `gpt-4`)

**Programmatic Configuration**:
```scala
val agentConfig = AgentConfig(systemPrompt = "...", maxIterations = 10)
val modelConfig = ModelConfig.fromEnv()
val agent = ReActAgent(modelConfig, agentConfig, tools)
```

## How It Works

**ReAct Loop**: User Input → LLM Decision → Tool Execution (if needed) → Observation → Iterate

**Type Safety**: Tools define typed input/output with automatic schema generation. The agent verifies tool invocations at compile time, preventing runtime errors.

## Advanced Features

### Code Evaluation with Type-Safe Programmatic Tool Calling

The `eval` tool implements **programmatic tool calling** - a pattern where the LLM writes code to orchestrate tools rather than making individual JSON-based tool calls. This approach, [mentioned by Anthropic](https://www.anthropic.com/engineering/advanced-tool-use), offers significant advantages over traditional tool use.

#### The Problem with Traditional Tool Calling

Standard LLM tool use has two critical issues:

1. **Context Pollution**: Each tool call's intermediate results accumulate in the conversation context, consuming tokens whether useful or not
2. **Inference Overhead**: Every tool invocation requires a full model inference pass, making multi-step workflows slow and error-prone

#### SafeAgent's Solution: Code-Based Orchestration with Type Safety

Instead of JSON tool requests, the LLM writes Scala code that orchestrates multiple tools. SafeAgent enhances this pattern with **compile-time type safety** through the `eval` tool:

- **Isolated Execution**: Code runs in a separate `scala-cli` process for safety and stability
- **Type-Safe Tool Access**: Auto-generated case classes and wrapper functions for each tool
- **Tool Discovery**: The `get_tool_library` tool returns all typed function signatures
- **Tool Server**: A lightweight HTTP server ([ToolServer.scala](src/main/scala/agent/tools/ToolServer.scala)) bridges the eval process back to the agent's tools

#### How It Works

When the agent invokes `eval`:
1. A `ToolServer` starts to handle tool requests from the eval process
2. The generated tool library (typed case classes + wrapper functions) is injected into the eval environment
3. LLM-generated code calls typed functions, which internally use `callTool(toolName, argsJson)` to communicate with the main agent
4. Only the final output returns to the model, keeping intermediate results out of context

```scala
// Auto-generated typed tool wrappers (via get_tool_library):
case class CalculatorInput(operation: String, a: Double, b: Double) derives ReadWriter
case class CalculatorOutput(result: Double) derives ReadWriter

def calculator(operation: String, a: Double, b: Double): CalculatorOutput

// LLM-generated code using typed tools:
// Calculate the sum from 5 to 200 using the calculator tool
var sum: Double = 0
for (i <- 5 to 200) {  
  val result = calculator(operation = "add", a = sum, b = i.toDouble)
  sum = result.result
}
println(s"The sum from 5 to 200 is: $sum")
```

#### Benefits

| Aspect | Traditional Tool Calling | SafeAgent's Approach |
|--------|-------------------------|---------------------|
| Type Safety | Runtime JSON parsing | Compile-time verification |
| Multi-step Logic | Multiple inference passes | Single code generation |
| Context Usage | Accumulates all results | Only final output returned |
| Error Detection | Runtime failures | Compilation errors |
| Complex Logic | Natural language reasoning | Native loops, conditionals |

### State Persistence

Automatic save/load to `agent_state.json` maintains context across sessions.
For example, storing memory of important information in one session allows retrieval in the next.

## Development

**Build Commands**:

```bash
sbt compile    # Compile

sbt test       # Run tests

sbt run        # Start REPL
```

## Acknowledgments

Inspired by the ReAct pattern ([Yao et al., 2023](https://arxiv.org/abs/2210.03629)) and langchain/langgraph.