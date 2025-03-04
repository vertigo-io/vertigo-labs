package io.vertigo.ai.llm.plugin.lc4j.rag;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import io.vertigo.core.lang.Assertion;
import io.vertigo.datastore.filestore.model.VFile;

final class VFileDocumentSource implements DocumentSource {

	private final VFile vFile;
	private final Metadata metadatas;

	public VFileDocumentSource(final VFile vFile, final Map<String, Object> metadatas) {
		Assertion.check()
				.isNotNull(vFile)
				.isNotNull(metadatas);
		//---
		this.vFile = vFile;
		this.metadatas = new Metadata();
		for (final Map.Entry<String, Object> entry : metadatas.entrySet()) {
			final var key = entry.getKey();
			final var value = entry.getValue();
			Assertion.check().isNotNull(key).isNotNull(value);
			if (value instanceof final UUID valueUUID) {
				this.metadatas.put(key, valueUUID);
			} else if (value instanceof final Integer valueInteger) {
				this.metadatas.put(key, valueInteger);
			} else if (value instanceof final Long valueLong) {
				this.metadatas.put(key, valueLong);
			} else if (value instanceof final Float valueFloat) {
				this.metadatas.put(key, valueFloat);
			} else if (value instanceof final Double valueDouble) {
				this.metadatas.put(key, valueDouble);
			} else {
				this.metadatas.put(key, value.toString());
			}
		}
		this.metadatas.put(Document.FILE_NAME, vFile.getFileName());
	}

	@Override
	public InputStream inputStream() throws IOException {
		return vFile.createInputStream();
	}

	@Override
	public Metadata metadata() {
		return metadatas;
	}

}
