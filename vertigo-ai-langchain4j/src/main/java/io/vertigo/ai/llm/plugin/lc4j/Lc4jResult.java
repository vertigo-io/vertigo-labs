package io.vertigo.ai.llm.plugin.lc4j;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

import dev.langchain4j.service.Result;
import io.vertigo.ai.llm.model.VLlmResult;

/**
 * Result of a langchain4j query.
 *
 * @see VLlmResult
 * @author skerdudou
 */
public class Lc4jResult implements VLlmResult {
	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
	private static final Parser MD_PARSER = Parser.builder()
			.extensions(EXTENSIONS)
			.build();
	private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
			.omitSingleParagraphP(true)
			.extensions(EXTENSIONS)
			.build();
	private static final TextContentRenderer TEXT_RENDERER = TextContentRenderer.builder().build();

	private final Result<String> result;

	public Lc4jResult(final Result<String> result) {
		this.result = result;
	}

	@Override
	public String getText() {
		final Node document = MD_PARSER.parse(getMarkdown());
		return TEXT_RENDERER.render(document);
	}

	@Override
	public String getMarkdown() {
		return result.content();
	}

	@Override
	public String getHtml() {
		final Node document = MD_PARSER.parse(getMarkdown());
		return HTML_RENDERER.render(document);
	}

	@Override
	public List<String> getSources() {
		return result.sources().stream()
				.map(s -> s.textSegment().metadata().getString("file_name"))
				.distinct()
				.toList();
	}

	/**
	 * Get the Langchain4j raw result.
	 *
	 * @return the raw result
	 */
	public Result<String> getRawResult() {
		return result;
	}

}
