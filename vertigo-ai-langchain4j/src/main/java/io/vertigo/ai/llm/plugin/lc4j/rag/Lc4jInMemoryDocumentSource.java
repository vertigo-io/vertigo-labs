package io.vertigo.ai.llm.plugin.lc4j.rag;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.vertigo.ai.llm.model.rag.VLlmDocument;
import io.vertigo.datastore.filestore.model.FileInfoURI;

public class Lc4jInMemoryDocumentSource extends Lc4jDocumentSource {

	private final Map<String, VLlmDocument> docMap = new HashMap<>();

	public Lc4jInMemoryDocumentSource(final EmbeddingModel embeddingModel) {
		super(new InMemoryEmbeddingStore<>(), embeddingModel);
	}

	@Override
	public void addDocument(final VLlmDocument document) {
		super.addDocument(document);

		final var fileUrn = document.metadatas().get(FILE_URN_METADATA).toString();
		docMap.put(fileUrn, document);
	}

	@Override
	public void removeDocument(final FileInfoURI fileInfoURI) {
		super.removeDocument(fileInfoURI);

		docMap.remove(fileInfoURI.toURN());
	}

	@Override
	protected VLlmDocument retreriveDocument(final TextSegment segment) {
		return docMap.get(segment.metadata().getString(FILE_URN_METADATA));
	}

	@Override
	public boolean isEmpty() {
		return docMap.isEmpty();
	}

}
