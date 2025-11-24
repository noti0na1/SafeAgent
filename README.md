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
Result: 460

> Store "meeting" with value "3pm tomorrow"
I've stored that information.
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
| `eval` | Execute Scala code in a separate process with full tool access |

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

### State Persistence**

Automatic save/load to `agent_state.json` maintains context across sessions. 
For example, storing memory of important information in one session allows retrieval in the next.

### Code Evaluation Tool

The `eval` tool enables dynamic execution of Scala code with full access to the agent's tool ecosystem. This allows the agent to handle complex logic that would be difficult to express through standard tool compositions.

**Key Features**:
- **Isolated Execution**: Code runs in a separate process for safety and stability
- **Tool Access**: Evaluated code can invoke any agent tool through a local network API
- **Complex Logic**: Handle multi-step computations, data transformations, and control flow
- **Bidirectional Communication**: The evaluation server communicates with the main agent process via HTTP

**How It Works**: When the agent invokes `eval`, a `ToolServer` is started by the agent. The evaluated code can call `callTool(toolName, argsJson)` to execute tools, which sends requests back to the main agent. This architecture ensures that complex operations remain type-safe while maintaining process isolation.

## Development

**Build Commands**:

```bash
sbt compile    # Compile

sbt test       # Run tests

sbt run        # Start REPL
```

## Acknowledgments

Inspired by the ReAct pattern ([Yao et al., 2023](https://arxiv.org/abs/2210.03629)).