package io.vertigo.ai.llm.plugin.lc4j;

import dev.langchain4j.service.TokenStream;

public interface AssistantStream {

	TokenStream rawAnswerStream(String query);
}
