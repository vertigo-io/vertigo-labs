package io.vertigo.ai.llm.plugin.lc4j;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import io.vertigo.ai.llm.model.LlmChat;
import io.vertigo.ai.llm.model.VLlmResult;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.ai.llm.plugin.lc4j.document.Lc4jDocumentUtil;
import io.vertigo.ai.llm.plugin.lc4j.document.VFileDocumentLoader;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.datastore.filestore.model.VFile;

public class Lc4jChat extends LlmChat {

	private final Assistant assistant;

	protected Lc4jChat(final List<VFile> files, final ChatLanguageModel chatModel, final VPromptContext context) {
		super(files, context);

		final List<Document> documents = files.stream()
				.map(VFileDocumentLoader::loadDocument)
				.toList();

		final var chatMemory = MessageWindowChatMemory.withMaxMessages(10);
		final var persona = context.getPersona();
		if (persona != null) {
			chatMemory.add(Lc4jUtils.getSystemMessageFromPersona(persona));
		}

		final var assistantBuilder = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel) // it should use OpenAI LLM
				.chatMemory(chatMemory)
				.tools(new Lc4jTools());
		if (!documents.isEmpty()) {
			assistantBuilder.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)); // it should have access to our documents
		}
		assistant = assistantBuilder.build();
	}

	@Override
	protected VLlmResult doChat(final String instructions) {
		Result<String> llmResponse;
		try {
			llmResponse = assistant.rawAnswer(instructions);
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		return new Lc4jResult(llmResponse);
	}

}
