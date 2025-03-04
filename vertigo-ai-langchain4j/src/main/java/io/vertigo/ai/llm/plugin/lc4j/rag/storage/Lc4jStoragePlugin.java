package io.vertigo.ai.llm.plugin.lc4j.rag.storage;

import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.core.node.component.Plugin;

public interface Lc4jStoragePlugin extends Plugin {

	VLlmDocumentSource getDocumentSource();

}
