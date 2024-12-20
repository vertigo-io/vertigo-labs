package io.vertigo.ai.llm.plugin.lc4j.document;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public final class Lc4jDocumentUtil {

	private Lc4jDocumentUtil() {
		// utility class
	}

	public static ContentRetriever createContentRetriever(final List<Document> documents) {
		// Here, we create and empty in-memory store for our documents and their embeddings.
		final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

		// Here, we are ingesting our documents into the store.
		// Under the hood, a lot of "magic" is happening, but we can ignore it for now.
		EmbeddingStoreIngestor.ingest(documents, embeddingStore);

		// Lastly, let's create a content retriever from an embedding store.
		return EmbeddingStoreContentRetriever.from(embeddingStore);
	}

}
