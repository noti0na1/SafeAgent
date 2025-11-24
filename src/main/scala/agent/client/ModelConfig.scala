package agent.client

/**
 *  Configuration for LLM model connection settings.
 *
 *  @param apiKey API key for authentication
 *  @param baseUrl Base URL for the API endpoint
 *  @param model Model identifier
 *  @param maxTokens Optional maximum tokens to generate
 *  @param temperature Optional sampling temperature (0.0 to 2.0)
 */
case class ModelConfig(
  apiKey: String,
  baseUrl: String,
  model: String,
  maxTokens: Option[Int] = None,
  temperature: Option[Double] = None
)

object ModelConfig:
  /** Create configuration from environment variables. */
  def fromEnv(): ModelConfig =
    val apiKey = sys.env.getOrElse("OPENAI_API_KEY",
      throw new IllegalStateException("OPENAI_API_KEY environment variable not set. Set it with: export OPENAI_API_KEY=your-key"))
    val baseUrl = sys.env.getOrElse("OPENAI_BASE_URL",
      throw new IllegalStateException("OPENAI_BASE_URL environment variable not set. Set it with: export OPENAI_BASE_URL=https://api.openai.com/v1"))
    val model = sys.env.getOrElse("OPENAI_MODEL",
      throw new IllegalStateException("OPENAI_MODEL environment variable not set. Set it with: export OPENAI_MODEL=gpt-4"))

    ModelConfig(
      apiKey = apiKey,
      baseUrl = baseUrl,
      model = model
    )
