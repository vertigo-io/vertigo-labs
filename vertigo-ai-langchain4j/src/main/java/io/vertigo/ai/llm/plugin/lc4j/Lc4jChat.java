package io.vertigo.ai.llm.plugin.lc4j;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import io.vertigo.ai.llm.model.LlmChat;
import io.vertigo.ai.llm.model.VLlmResult;
import io.vertigo.ai.llm.model.VPrompt;
import io.vertigo.ai.llm.plugin.lc4j.document.Lc4jDocumentUtil;
import io.vertigo.ai.llm.plugin.lc4j.document.VFileDocumentLoader;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.datastore.filestore.model.VFile;

public class Lc4jChat extends LlmChat {

	private final Assistant assistant;

	protected Lc4jChat(final Long id, final List<VFile> files, final ChatLanguageModel chatModel) {
		super(id, files);

		final List<Document> documents = files.stream()
				.map(VFileDocumentLoader::loadDocument)
				.toList();

		final var assistantBuilder = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel) // it should use OpenAI LLM
				.chatMemory(MessageWindowChatMemory.withMaxMessages(10)) // it should remember 10 latest messages
		;
		if (!documents.isEmpty()) {
			assistantBuilder.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)); // it should have access to our documents
		}
		assistant = assistantBuilder.build();
	}

	@Override
	protected VLlmResult doChat(final VPrompt prompt) {
		Result<String> llmResponse;
		try {
			llmResponse = assistant.rawAnswer(prompt.instructions());
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		return new Lc4jResult(llmResponse);
	}

}
