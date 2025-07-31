package io.vertigo.ai.llm.plugin.lc4j.rag;

import java.util.Collection;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.data.document.parser.DocumentParserFactory;
import io.vertigo.ai.impl.llm.LlmManagerImpl;
import io.vertigo.ai.llm.model.rag.VLlmDocument;
import io.vertigo.core.analytics.AnalyticsManager;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.node.Node;

public final class VFileDocumentLoader {
	private static final DocumentParser DEFAULT_DOCUMENT_PARSER = Utils.getOrDefault(loadDocumentParser(), TextDocumentParser::new);

	private VFileDocumentLoader() {
	}

	public static Document loadDocument(final VLlmDocument document) {
		return getAnalyticsManager().traceWithReturn(LlmManagerImpl.LLM_CATEGORY, "readFile",
				t -> {
					try {
						t.setTag("isEmpty", "false");
						return DocumentLoader.load(new VFileDocumentSource(document.fileInfo().getVFile(), document.metadatas()), DEFAULT_DOCUMENT_PARSER);
					} catch (final BlankDocumentException e) {
						t.setTag("isEmpty", "true");
						return null;
					} catch (final Exception e) {
						throw new VSystemException("Unable to read text from the document: " + document.fileInfo().getURI(), e);
					}
				});
	}

	private static DocumentParser loadDocumentParser() {

		final Collection<DocumentParserFactory> factories = ServiceHelper.loadFactories(DocumentParserFactory.class);

		if (factories.size() > 1) {
			throw new VSystemException("Conflict: multiple document parsers have been found in the classpath. " +
					"Please explicitly specify the one you wish to use.");
		}

		for (final DocumentParserFactory factory : factories) {
			return factory.create();
		}

		return null;
	}

	private static AnalyticsManager getAnalyticsManager() {
		return Node.getNode().getComponentSpace().resolve(AnalyticsManager.class);
	}
}
