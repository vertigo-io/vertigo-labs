package io.vertigo.ai.llm.plugin.lc4j.rag.storage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.vertigo.ai.llm.model.rag.VLlmDocument;
import io.vertigo.ai.llm.plugin.lc4j.rag.Lc4jDocumentSource;
import io.vertigo.datastore.filestore.FileStoreManager;
import io.vertigo.datastore.filestore.model.FileInfoURI;

public class Lc4jPgVectorDocumentSource extends Lc4jDocumentSource {

	private final FileStoreManager fileStoreManager;

	Lc4jPgVectorDocumentSource(final FileStoreManager fileStoreManager, final PgVectorEmbeddingStore embeddingStore, final EmbeddingModel embeddingModel) {
		super(embeddingStore, embeddingModel);
		this.fileStoreManager = fileStoreManager;
	}

	@Override
	protected VLlmDocument retreriveDocument(final TextSegment segment) {
		final var metadata = segment.metadata();
		final var fileUrn = metadata.getString(FILE_URN_METADATA);
		final var fileInfoURI = FileInfoURI.fromURN(fileUrn);
		final var fileInfo = fileStoreManager.read(fileInfoURI);

		return new VLlmDocument(fileInfo, metadata.toMap());
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

}
