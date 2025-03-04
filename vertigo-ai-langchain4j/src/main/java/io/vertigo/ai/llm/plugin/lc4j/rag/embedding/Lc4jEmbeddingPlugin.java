package io.vertigo.ai.llm.plugin.lc4j.rag.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.vertigo.core.node.component.Plugin;

public interface Lc4jEmbeddingPlugin extends Plugin {

	EmbeddingModel getEmbeddingModel();

}
