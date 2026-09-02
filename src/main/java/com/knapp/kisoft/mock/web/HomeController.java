package com.knapp.kisoft.mock.web;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serves the project homepage at the application root ({@code /kisoft/}). It renders the contents
 * of README.md (Markdown, incl. GFM tables) as HTML and links to the Swagger UI / OpenAPI spec.
 */
@Controller
public class HomeController {

    private final String contextPath;
    private final Parser parser;
    private final HtmlRenderer renderer;

    public HomeController(@Value("${server.servlet.context-path:}") String contextPath) {
        this.contextPath = contextPath == null ? "" : contextPath;
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    @GetMapping(value = {"/", "/home"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String home() {
        String readmeHtml = renderer.render(parser.parse(loadReadme()));
        return PAGE
                .replace("{{SWAGGER_URL}}", contextPath + "/swagger-ui.html")
                .replace("{{API_DOCS_URL}}", contextPath + "/v3/api-docs")
                .replace("{{CONTENT}}", readmeHtml);
    }

    /** README from the classpath (bundled into the jar), falling back to the working directory. */
    private String loadReadme() {
        try (InputStream in = new ClassPathResource("README.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // not on classpath — try the filesystem
        }
        try {
            Path p = Path.of("README.md");
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // fall through to the placeholder
        }
        return "# KNAPP KiSoft Mock Server\n\n*README.md could not be located on the classpath or working directory.*";
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>KNAPP KiSoft One Mock Server</title>
              <style>
                :root { --bg:#fff; --fg:#1f2328; --muted:#656d76; --border:#d0d7de;
                        --code-bg:#f6f8fa; --link:#0969da; --accent:#1f883d; --header:#0d1117; }
                * { box-sizing: border-box; }
                body { margin:0; color:var(--fg); background:var(--bg);
                       font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif;
                       line-height:1.55; }
                header.site { position:sticky; top:0; z-index:10; background:var(--header); color:#fff;
                              display:flex; align-items:center; gap:16px; flex-wrap:wrap;
                              padding:14px 24px; box-shadow:0 1px 4px rgba(0,0,0,.15); }
                header.site .brand { font-weight:700; font-size:16px; margin-right:auto; }
                header.site .brand span { color:#7ee2a8; }
                .btn { display:inline-block; text-decoration:none; font-weight:600; font-size:14px;
                       padding:8px 16px; border-radius:6px; }
                .btn-primary { background:var(--accent); color:#fff; }
                .btn-primary:hover { background:#1a7f37; }
                .btn-ghost { background:transparent; color:#fff; border:1px solid #30363d; }
                .btn-ghost:hover { border-color:#8b949e; }
                main { max-width:960px; margin:0 auto; padding:32px 24px 80px; }
                main h1 { font-size:2em; padding-bottom:.3em; border-bottom:1px solid var(--border); }
                main h2 { font-size:1.5em; margin-top:1.6em; padding-bottom:.3em; border-bottom:1px solid var(--border); }
                main h3 { font-size:1.2em; margin-top:1.4em; }
                main a { color:var(--link); text-decoration:none; }
                main a:hover { text-decoration:underline; }
                main code { background:var(--code-bg); padding:.2em .4em; border-radius:6px;
                            font-size:85%; font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace; }
                main pre { background:var(--code-bg); padding:16px; border-radius:6px; overflow:auto; }
                main pre code { background:none; padding:0; font-size:85%; }
                main table { border-collapse:collapse; width:100%; margin:16px 0; display:block; overflow:auto; }
                main th, main td { border:1px solid var(--border); padding:6px 13px; text-align:left; vertical-align:top; }
                main th { background:var(--code-bg); font-weight:600; }
                main tr:nth-child(2n) td { background:#fbfcfd; }
                main blockquote { margin:0; padding:0 1em; color:var(--muted); border-left:.25em solid var(--border); }
                main img { max-width:100%; }
                footer { max-width:960px; margin:0 auto; padding:24px; color:var(--muted); font-size:13px;
                         border-top:1px solid var(--border); }
              </style>
            </head>
            <body>
              <header class="site">
                <div class="brand">KNAPP <span>KiSoft One</span> Mock</div>
                <a class="btn btn-primary" href="{{SWAGGER_URL}}">Open Swagger UI &rarr;</a>
                <a class="btn btn-ghost" href="{{API_DOCS_URL}}">OpenAPI JSON</a>
              </header>
              <main>{{CONTENT}}</main>
              <footer>Rendered from <code>README.md</code> &middot; API docs at <a href="{{SWAGGER_URL}}">{{SWAGGER_URL}}</a></footer>
            </body>
            </html>
            """;
}
