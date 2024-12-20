package io.vertigo.ai.llm.plugin.lc4j.document;

import java.util.Collection;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.spi.ServiceHelper;
import dev.langchain4j.spi.data.document.parser.DocumentParserFactory;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.datastore.filestore.model.VFile;

public final class VFileDocumentLoader {
	private static final DocumentParser DEFAULT_DOCUMENT_PARSER = Utils.getOrDefault(loadDocumentParser(), TextDocumentParser::new);

	private VFileDocumentLoader() {
	}

	public static Document loadDocument(final VFile vFile) {

		return DocumentLoader.load(new VFileDocumentSource(vFile), DEFAULT_DOCUMENT_PARSER);
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
}
