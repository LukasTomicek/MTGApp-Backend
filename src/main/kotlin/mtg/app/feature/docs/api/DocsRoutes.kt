package mtg.app.feature.docs.api

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerDocsRoutes() {
    get("/openapi.yaml") {
        val spec = javaClass.classLoader
            .getResourceAsStream("openapi.yaml")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("openapi.yaml resource not found")
        call.respondText(
            text = spec,
            contentType = ContentType.parse("application/yaml"),
        )
    }

    get("/docs") {
        call.respondText(
            text = swaggerHtml,
            contentType = ContentType.Text.Html,
        )
    }
}

private val swaggerHtml = """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>MTG Backend API Docs</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
  <style>
    html, body { margin: 0; padding: 0; background: #fafafa; }
    .topbar { display: none; }
  </style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    window.ui = SwaggerUIBundle({
      url: '/openapi.yaml',
      dom_id: '#swagger-ui',
      deepLinking: true,
      presets: [SwaggerUIBundle.presets.apis],
    });
  </script>
</body>
</html>
""".trimIndent()
