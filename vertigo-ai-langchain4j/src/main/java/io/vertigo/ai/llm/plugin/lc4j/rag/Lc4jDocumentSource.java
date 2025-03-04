package io.vertigo.ai.llm.plugin.lc4j.rag;

import java.util.List;

import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.vertigo.ai.impl.llm.LlmManagerImpl;
import io.vertigo.ai.llm.model.rag.VLlmDocument;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSearchResult;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.core.analytics.AnalyticsManager;
import io.vertigo.core.lang.VUserException;
import io.vertigo.core.node.Node;
import io.vertigo.datastore.filestore.model.FileInfoURI;

public abstract class Lc4jDocumentSource implements VLlmDocumentSource {

	public static final String FILE_URN_METADATA = "file_urn";

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
	public List<VLlmDocumentSearchResult> search(final String filter, final Integer maxResults, final Double minScore) {
		return getContentRetriever(maxResults, minScore).retrieve(Query.from(filter)).stream()
				.map(this::buildResult)
				.toList();
	}

	/**
	 * @return the contentRetriever
	 */
	public ContentRetriever getContentRetriever(final Integer maxResults, final Double minScore) {
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(maxResults)
				.minScore(minScore)
				.build();
	};

	protected VLlmDocumentSearchResult buildResult(final Content content) {
		return new VLlmDocumentSearchResult(retreriveDocument(content), content.textSegment().text());
	}

	protected abstract VLlmDocument retreriveDocument(final Content content);

	private static AnalyticsManager getAnalyticsManager() {
		return Node.getNode().getComponentSpace().resolve(AnalyticsManager.class);
	}
}
