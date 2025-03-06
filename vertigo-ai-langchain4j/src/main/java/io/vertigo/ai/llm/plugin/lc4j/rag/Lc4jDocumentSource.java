package io.vertigo.ai.llm.plugin.lc4j.rag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import io.vertigo.ai.impl.llm.LlmManagerImpl;
import io.vertigo.ai.llm.model.rag.VLlmDocument;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSearchResult;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.core.analytics.AnalyticsManager;
import io.vertigo.core.lang.VUserException;
import io.vertigo.core.node.Node;
import io.vertigo.datastore.filestore.model.FileInfoURI;

public abstract class Lc4jDocumentSource implements VLlmDocumentSource {

	protected final EmbeddingStore<TextSegment> embeddingStore;
	protected final EmbeddingModel embeddingModel;
	protected final EmbeddingStoreIngestor ingestor;

	protected Lc4jDocumentSource(final EmbeddingStore<TextSegment> embeddingStore, final EmbeddingModel embeddingModel) {
		this.embeddingStore = embeddingStore;
		this.embeddingModel = embeddingModel;

		ingestor = EmbeddingStoreIngestor.builder()
				.documentSplitter(new DocumentByParagraphSplitter(1024, 64))
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.build();
	}

	@Override
	public void addDocument(final VLlmDocument vLlmDocument) {
		getAnalyticsManager().trace(LlmManagerImpl.LLM_CATEGORY, "addDocument", t -> {
			final var fileUrn = vLlmDocument.fileInfo().getURI().toURN();
			vLlmDocument.metadatas().put(FILE_URN_METADATA, fileUrn);

			final var document = VFileDocumentLoader.loadDocument(vLlmDocument);
			if (document == null) {
				throw new VUserException("Unable to read text from the document.");
			}
			getAnalyticsManager().trace(LlmManagerImpl.LLM_CATEGORY, "ingestDocument",
					t2 -> ingestor.ingest(document));
		});
	}

	@Override
	public void removeDocument(final FileInfoURI fileInfoURI) {
		final var fileUrn = fileInfoURI.toURN();
		embeddingStore.removeAll(MetadataFilterBuilder.metadataKey(FILE_URN_METADATA).isEqualTo(fileUrn));
	}

	@Override
	public List<VLlmDocumentSearchResult> search(final String query, final Map<String, Object> metadataFilter, final Integer maxResults, final Double minScore) {
		final Embedding embeddedQuery = embeddingModel.embed(query).content();

		final EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
				.queryEmbedding(embeddedQuery)
				.maxResults(maxResults)
				.minScore(minScore)
				.filter(toFilter(metadataFilter))
				.build();

		final EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

		return searchResult.matches().stream()
				.map(m -> new VLlmDocumentSearchResult(retreriveDocument(m.embedded()), m.embedded().text(), m.score()))
				.toList();
	}

	/**
	 * Returns the lc4j content retriever.
	 *
	 * @param metadataFilter the metadata filter
	 * @param maxResults the maximum number of results
	 * @param minScore the minimum score
	 * @return the contentRetriever
	 */
	public ContentRetriever getContentRetriever(final Map<String, Object> metadataFilter, final Integer maxResults, final Double minScore) {
		final var builder = EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel);

		if (metadataFilter != null && !metadataFilter.isEmpty()) {
			builder.filter(toFilter(metadataFilter));
		}
		return builder
				.maxResults(maxResults)
				.minScore(minScore)
				.build();
	}

	private Filter toFilter(final Map<String, Object> metadataFilter) {
		Filter curentFilter = null;
		for (final var entry : metadataFilter.entrySet()) {
			final Filter newFilter;
			if (entry.getValue() instanceof Collection) {
				newFilter = new IsIn(entry.getKey(), (Collection<?>) resolveValue(entry.getValue()));
			} else {
				newFilter = new IsEqualTo(entry.getKey(), resolveValue(entry.getValue()));
			}
			if (curentFilter == null) {
				curentFilter = newFilter;
			} else {
				curentFilter = curentFilter.and(newFilter);
			}
		}
		return curentFilter;
	}

	private Object resolveValue(final Object value) {
		if (value instanceof final Collection<?> collection) {
			return collection.stream()
					.map(this::resolveValue)
					.toList();
		}

		if (value instanceof final FileInfoURI fileInfoURI) {
			return fileInfoURI.toURN();
		}

		return value;
	}

	protected abstract VLlmDocument retreriveDocument(final TextSegment segment);

	private static AnalyticsManager getAnalyticsManager() {
		return Node.getNode().getComponentSpace().resolve(AnalyticsManager.class);
	}
}
