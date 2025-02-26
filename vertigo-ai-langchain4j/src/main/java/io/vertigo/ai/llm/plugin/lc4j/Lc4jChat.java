package io.vertigo.ai.llm.plugin.lc4j;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import io.vertigo.ai.llm.model.LlmChat;
import io.vertigo.ai.llm.model.VLlmMessage;
import io.vertigo.ai.llm.model.VLlmMessageStreamConfig;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.ai.llm.plugin.lc4j.document.Lc4jDocumentUtil;
import io.vertigo.ai.llm.plugin.lc4j.document.VFileDocumentLoader;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.datastore.filestore.model.VFile;

public class Lc4jChat extends LlmChat {

	private final Assistant assistant;
	private final AssistantStream assistantStream;

	protected Lc4jChat(final List<VFile> files, final ChatLanguageModel chatModel, final StreamingChatLanguageModel chatModelStream, final VPromptContext context) {
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
				.chatLanguageModel(chatModel)
				.chatMemory(chatMemory)
				.tools(new Lc4jTools());
		if (!documents.isEmpty()) {
			assistantBuilder.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)); // it should have access to our documents
		}
		assistant = assistantBuilder.build();

		final var assistantStreamBuilder = AiServices.builder(AssistantStream.class)
				.streamingChatLanguageModel(chatModelStream)
				.chatMemory(chatMemory)
				.tools(new Lc4jTools());
		if (!documents.isEmpty()) {
			assistantStreamBuilder.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)); // it should have access to our documents
		}
		assistantStream = assistantStreamBuilder.build();
	}

	@Override
	protected VLlmMessage doChat(final String instructions) {
		Result<String> llmResponse;
		try {
			llmResponse = assistant.rawAnswer(instructions);
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		return new Lc4jMessage(llmResponse);
	}

	@Override
	protected void doChatStream(final String instructions, final VLlmMessageStreamConfig<VLlmMessage> streamConfig) {
		final StringBuilder messageAccumulator = new StringBuilder();
		final List<Content> sources = new ArrayList<>();

		assistantStream.rawAnswerStream(instructions)
				.onNext(token -> {
					messageAccumulator.append(token);
					streamConfig.tokenHandler().accept(token);
					streamConfig.partialMessageHandler().accept(new Lc4jMessage(messageAccumulator.toString(), sources));
				})
				.onComplete(response -> {
					// as langchain4j 0.36.2 (or 1.0-beta1), response is only the last response after tool executions and not the full conversation, so we return what we have accumulated from onNext
					// https://github.com/langchain4j/langchain4j/issues/2610
					streamConfig.messageHandler().accept(new Lc4jMessage(messageAccumulator.toString(), sources));
				})
				.onRetrieved(sources::addAll)
				.onError(streamConfig.errorHandler())
				.start();
	}

}
