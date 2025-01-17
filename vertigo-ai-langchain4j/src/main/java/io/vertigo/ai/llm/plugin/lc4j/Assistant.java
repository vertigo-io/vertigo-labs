package io.vertigo.ai.llm.plugin.lc4j;

import dev.langchain4j.service.Result;

public interface Assistant {

	String answer(String query);

	Result<String> rawAnswer(String query);
}
