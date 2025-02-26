package io.vertigo.ai.llm.plugin.lc4j;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.vertigo.ai.llm.model.VLlmMessage;

/**
 * Result of a langchain4j query.
 *
 * @see VLlmMessage
 * @author skerdudou
 */
public class Lc4jMessage implements VLlmMessage {
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
	private final String text;
	private final List<Content> sources;

	public Lc4jMessage(final Result<String> result) {
		this.result = result;
		text = result.content();
		sources = result.sources();
	}

	public Lc4jMessage(final String text, final List<Content> sources) {
		result = null;
		this.text = text;
		this.sources = sources;
	}

	@Override
	public String getText() {
		final Node document = MD_PARSER.parse(getMarkdown());
		return TEXT_RENDERER.render(document);
	}

	@Override
	public String getMarkdown() {
		return text;
	}

	@Override
	public String getHtml() {
		final Node document = MD_PARSER.parse(getMarkdown());
		return HTML_RENDERER.render(document);
	}

	@Override
	public List<String> getSources() {
		return sources.stream()
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
