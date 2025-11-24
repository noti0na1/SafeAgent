package agent.tools

import agent.core.{Tool, ToolDataType, State}
import upickle.default.*
import scala.util.{Try, Success}
import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

/**
 *  Input parameters for date/time queries.
 *
 *  @param timezone Optional timezone (e.g., "UTC", "America/New_York"). Defaults to system timezone
 *  @param format Optional format: "iso" (default) or "readable"
 */
case class DateTimeInput(
  timezone: Option[String] = None,
  format: Option[String] = None
) derives ToolDataType

/**
 *  Current date and time information.
 *
 *  @param datetime The formatted date/time string
 *  @param timezone The timezone used
 */
case class DateTimeOutput(
  datetime: String,
  timezone: String
) derives ToolDataType

/**
 *  Tool for getting the current date and time.
 *
 *  Supports multiple timezones and output formats.
 */
class DateTimeTool extends Tool[DateTimeInput, DateTimeOutput]:
  override val name: String = "get_datetime"

  override val description: String =
    "Gets the current date and time, optionally for a specific timezone."

  override def invoke(input: DateTimeInput)(using state: State): Try[DateTimeOutput] =
    Try {
      // Validate and get timezone with better error message
      val zoneId = input.timezone match
        case Some(tz) => ZoneId.of(tz)
        case None => ZoneId.systemDefault()

      val now = LocalDateTime.now(zoneId)
      val format = input.format.getOrElse("iso")

      val formattedDateTime = format match
        case "iso" =>
          now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        case "readable" =>
          now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"))
        case _ =>
          now.toString

      DateTimeOutput(
        datetime = formattedDateTime,
        timezone = zoneId.toString
      )
    }
