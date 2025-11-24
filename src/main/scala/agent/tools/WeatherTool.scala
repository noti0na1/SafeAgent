package agent.tools

import agent.core.{Tool, ToolDataType, State}

import upickle.default.*
import scala.util.{Try, Success}

/**
 *  Input parameters for weather queries.
 *
 *  @param location Location name (e.g., "San Francisco", "London")
 *  @param unit Optional temperature unit: "celsius" or "fahrenheit" (default: "fahrenheit")
 */
case class WeatherInput(
  location: String,
  unit: Option[String] = None
) derives ToolDataType

/**
 *  Weather information for a location.
 *
 *  @param location The queried location
 *  @param temperature Temperature value
 *  @param unit Temperature unit used
 *  @param condition Weather condition description
 */
case class WeatherOutput(
  location: String,
  temperature: Int,
  unit: String,
  condition: String
) derives ToolDataType

/**
 *  Mock weather tool for demonstration purposes.
 *
 *  Returns simulated weather data. In a real implementation,
 *  this would call an actual weather API.
 */
class WeatherTool extends Tool[WeatherInput, WeatherOutput]:
  override val name: String = "get_weather"

  override val description: String =
    "Gets the current weather for a specified location. This is a mock tool that returns simulated weather data."

  override def invoke(input: WeatherInput)(using state: State): Try[WeatherOutput] =
    Success {
      val unit = input.unit.getOrElse("fahrenheit")

      // Mock weather data
      val temp = if unit == "celsius" then 22 else 72

      WeatherOutput(
        location = input.location,
        temperature = temp,
        unit = unit,
        condition = "sunny"
      )
    }
