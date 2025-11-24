package agent.tools

import agent.core.{Tool, ToolDataType, State}
import agent.core.ToolDataType.toolDataTypeToJsonSchema
import agent.core.ToolDataType.toolDataTypeToReadWriter
import upickle.default.*
import scala.util.{Try, Success}

/**
 *  Input parameters for search queries.
 *
 *  @param query The search query string
 *  @param num_results Optional number of results to return (1-10, default: 3)
 */
case class SearchInput(
  query: String,
  num_results: Option[Int] = None
) derives ToolDataType

/**
 *  A single search result.
 *
 *  @param title Result title
 *  @param url Result URL
 *  @param snippet Short text excerpt
 */
case class SearchResult(
  title: String,
  url: String,
  snippet: String
) derives ToolDataType

/**
 *  Search results collection.
 *
 *  @param results List of search results
 */
case class SearchOutput(
  results: List[SearchResult]
) derives ToolDataType

/**
 *  Mock search tool for demonstration purposes.
 *
 *  Returns simulated search results. In a real implementation,
 *  this would call an actual search API.
 */
class SearchTool extends Tool[SearchInput, SearchOutput]:
  override val name: String = "search"

  override val description: String =
    "Searches the web for information. This is a mock tool that returns simulated search results."

  override def invoke(input: SearchInput)(using state: State): Try[SearchOutput] =
    Success {
      val numResults = input.num_results.getOrElse(3).min(10).max(1)

      val results = (1 to numResults).map { i =>
        SearchResult(
          title = s"Result $i for '${input.query}'",
          url = s"https://example.com/result$i",
          snippet = s"This is a mock search result snippet for '${input.query}'. It provides relevant information about the search topic."
        )
      }.toList

      SearchOutput(results = results)
    }
