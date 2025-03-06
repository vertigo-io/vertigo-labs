package io.vertigo.ai.llm.plugin.lc4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import io.vertigo.ai.impl.llm.LlmStandardChat;
import io.vertigo.ai.llm.model.VLlmMessage;
import io.vertigo.ai.llm.model.VLlmMessageStreamConfig;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.Lc4jDocumentSource;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;

public class Lc4jChat extends LlmStandardChat {

	private final Assistant assistant;
	private final AssistantStream assistantStream;

	protected Lc4jChat(final VLlmDocumentSource documentSource, final Map<String, Object> metadataFilter, final VPromptContext context,
			final ChatLanguageModel chatModel, final StreamingChatLanguageModel chatModelStream, final Tokenizer tokenizer) {
		super(documentSource, context);

		final var chatMemory = TokenWindowChatMemory.withMaxTokens(4000, tokenizer);

		Lc4jUtils.getSystemMessageFromContext(context)
				.ifPresent(chatMemory::add);

		final var lc4jTools = new Lc4jTools();

		final var assistantBuilder = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel)
				.chatMemory(chatMemory)
				.tools(lc4jTools);
		final var assistantStreamBuilder = AiServices.builder(AssistantStream.class)
				.streamingChatLanguageModel(chatModelStream)
				.chatMemory(chatMemory)
				.tools(lc4jTools);

		if (documentSource != null && !documentSource.isEmpty()) {
			Assertion.check()
					.isTrue(documentSource instanceof Lc4jDocumentSource, "Only Lc4jDocumentSource is supported");

			// add access to our documents
			final var contentRetriever = ((Lc4jDocumentSource) documentSource).getContentRetriever(metadataFilter, 10, 0.5d);
			assistantBuilder.contentRetriever(contentRetriever);
			assistantStreamBuilder.contentRetriever(contentRetriever);
		}

		assistant = assistantBuilder.build();
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

		final var valueHolder = new Object() {
			Instant lastTokenTime = Instant.now();
		}; // using an holder to be able to change the value inside the lambda as Instant is immutable
		assistantStream.rawAnswerStream(instructions)
				.onNext(token -> {
					messageAccumulator.append(token);
					final var now = Instant.now();
					if (now.minusMillis(streamConfig.throttleMs()).isAfter(valueHolder.lastTokenTime)) {
						valueHolder.lastTokenTime = now;
						streamConfig.tokenHandler().accept(token);
						streamConfig.partialMessageHandler().accept(new Lc4jMessage(messageAccumulator.toString(), sources));
					}
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
