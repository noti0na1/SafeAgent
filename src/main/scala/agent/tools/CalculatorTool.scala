package agent.tools

import agent.core.{Tool, ToolDataType, State}
import upickle.default.*
import scala.util.Try

/**
 *  Input parameters for calculator operations.
 *
 *  @param operation Operation to perform: "add", "subtract", "multiply", or "divide"
 *  @param a First operand
 *  @param b Second operand
 */
case class CalculatorInput(
  operation: String,
  a: Double,
  b: Double
) derives ToolDataType

/**
 *  Result from a calculator operation.
 *
 *  @param result The computed result
 *  @param operation The operation that was performed
 */
case class CalculatorOutput(
  result: Double,
  operation: String
) derives ToolDataType

/**
 *  Tool for performing basic arithmetic operations.
 *
 *  Supports addition, subtraction, multiplication, and division.
 *  Division by zero returns an error.
 */
class CalculatorTool extends Tool[CalculatorInput, CalculatorOutput]:
  override val name: String = "calculator"

  override val description: String =
    "Performs basic arithmetic operations (add, subtract, multiply, divide). Returns the result as a number."

  override def invoke(input: CalculatorInput)(using state: State): Try[CalculatorOutput] =
    Try {
      val result = input.operation match
        case "add" => input.a + input.b
        case "subtract" => input.a - input.b
        case "multiply" => input.a * input.b
        case "divide" =>
          if input.b == 0 then
            throw new IllegalArgumentException("Cannot divide by zero")
          input.a / input.b
        case op => throw new IllegalArgumentException(s"Unknown operation: $op")

      CalculatorOutput(
        result = result,
        operation = input.operation
      )
    }
