package io.vertigo.ai.llm.plugin.lc4j.document;

import java.io.IOException;
import java.io.InputStream;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import io.vertigo.datastore.filestore.model.VFile;

final class VFileDocumentSource implements DocumentSource {

	private final VFile vFile;

	public VFileDocumentSource(final VFile vFile) {
		this.vFile = vFile;
	}

	@Override
	public InputStream inputStream() throws IOException {
		return vFile.createInputStream();
	}

	@Override
	public Metadata metadata() {
		return new Metadata()
				.put(Document.FILE_NAME, vFile.getFileName());
	}

}
