/*
 * vertigo - application development platform
 *
 * Copyright (C) 2013-2024, Vertigo.io, team@vertigo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vertigo.ai.llm.plugin.lc4j;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenizer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.vertigo.ai.llm.LlmChat;
import io.vertigo.ai.llm.LlmPlugin;
import io.vertigo.ai.llm.model.VLlmMessage;
import io.vertigo.ai.llm.model.VPrompt;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.Lc4jDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.Lc4jInMemoryDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.embedding.Lc4jEmbeddingPlugin;
import io.vertigo.ai.llm.plugin.lc4j.rag.storage.Lc4jStoragePlugin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.VUserException;
import io.vertigo.core.node.Node;
import io.vertigo.core.param.ParamValue;
import io.vertigo.vega.engines.webservice.json.JsonEngine;

/**
 * Defines llm plugin using OpenAi with langchain4j.
 *
 * @author skerdudou
 */
public final class Lc4jPlugin implements LlmPlugin {
	private static final Logger LOG = LogManager.getLogger(Lc4jPlugin.class);

	private final String modelName;
	private final Tokenizer tokenizer;
	private final ChatLanguageModel chatModel;
	private final StreamingChatLanguageModel chatModelStream;

	private final ServiceOutputParser serviceOutputParser = new VServiceOutputParser();
	private final Lc4jEmbeddingPlugin embeddingPlugin;
	private final Optional<Lc4jStoragePlugin> storagePlugin;

	@Inject
	public Lc4jPlugin(
			@ParamValue("apiKey") final Optional<String> apiKeyOpt,
			@ParamValue("modelName") final Optional<String> modelNameOpt,
			@ParamValue("url") final Optional<String> urlOpt,
			@ParamValue("apiType") final Optional<String> apiTypeOpt,
			final Lc4jEmbeddingPlugin embeddingPlugin,
			final Optional<Lc4jStoragePlugin> storagePlugin) {

		//---
		final var apiType = apiTypeOpt.orElse("openai");

		switch (apiType.toLowerCase()) {
			case "openai" -> {
				Assertion.check()
						.isTrue(apiKeyOpt.isPresent(), "OpenAI API Key must be provided")
						.isNotBlank(apiKeyOpt.get()); // langchain4j can use "demo" api key with a 5000 tokens limit (with langchain4j server acting as a proxy)
				// --

				modelName = modelNameOpt.orElse("gpt-4o-mini");

				tokenizer = new OpenAiTokenizer(modelName);

				chatModel = OpenAiChatModel.builder()
						.baseUrl(urlOpt.orElse(null)) // null => default url
						.apiKey(apiKeyOpt.get())
						.modelName(modelName)
						.temperature(0d)
						.build();

				chatModelStream = OpenAiStreamingChatModel.builder()
						.baseUrl(urlOpt.orElse(null)) // null => default url
						.apiKey(apiKeyOpt.get())
						.modelName(modelName)
						.temperature(0d)
						.build();
			}
			case "ollama" -> {
				modelName = modelNameOpt.orElse("qwen2.5:7b");

				tokenizer = new HuggingFaceTokenizer(); // Using Bert tokenizer estimation (used only to estimate the number of tokens in the prompt)

				// OpenWebUi uses "Authorization: Bearer YOUR_API_KEY" header to authenticate requests
				final var customHeaders = apiKeyOpt
						.map(key -> Map.of("Authorization", "Bearer " + key))
						.orElseGet(Collections::emptyMap);

				chatModel = OllamaChatModel.builder()
						.baseUrl(urlOpt.orElse(null)) // null => default url
						.modelName(modelName)
						.customHeaders(customHeaders)
						.temperature(0d)
						.build();

				chatModelStream = OllamaStreamingChatModel.builder()
						.baseUrl(urlOpt.orElse(null)) // null => default url
						.modelName(modelName)
						.customHeaders(customHeaders)
						.temperature(0d)
						.build();
			}
			default -> throw new IllegalArgumentException("Unsupported API type: " + apiType);
		}

		this.embeddingPlugin = embeddingPlugin;
		this.storagePlugin = storagePlugin;
	}

	@Override
	public VLlmMessage askOnFiles(final VPrompt prompt, final VLlmDocumentSource documentSource, final Map<String, Object> metadataFilter) {
		final var assistantBuilder = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel);

		final var context = prompt.getContext();
		Lc4jUtils.getSystemMessageFromContext(context)
				.ifPresent(systemMessage -> assistantBuilder.systemMessageProvider(memId -> systemMessage.text()));

		if (documentSource != null && !documentSource.isEmpty()) {
			Assertion.check()
					.isTrue(documentSource instanceof Lc4jDocumentSource, "Only Lc4jDocumentSource is supported");

			// add access to our documents
			final var contentRetriever = ((Lc4jDocumentSource) documentSource).getContentRetriever(metadataFilter, 10, 0.5d);
			assistantBuilder.contentRetriever(contentRetriever);
		}
		final Assistant assistant = assistantBuilder.build();

		Result<String> llmResponse;
		try {
			llmResponse = assistant.rawAnswer(prompt.getInstructions());
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		return new Lc4jMessage(llmResponse);
	}

	@Override
	public <T> T ask(final VPrompt prompt, final Class<T> clazz) {
		final var chatMessages = new ArrayList<ChatMessage>();
		final var context = prompt.getContext();

		Lc4jUtils.getSystemMessageFromContext(context)
				.ifPresent(chatMessages::add);

		// append additional instructions as in dev.langchain4j.service.DefaultAiServices
		// don't know why it is not done in the system message
		final var additionalInstructions = serviceOutputParser.outputFormatInstructions(clazz);
		chatMessages.add(UserMessage.from(prompt.getInstructions() + "\n" + additionalInstructions));

		final var llmResponse = chatModel.generate(chatMessages);
		return (T) serviceOutputParser.parse(llmResponse, clazz);
	}

	@Override
	public LlmChat newChat(final VLlmDocumentSource documentSource, final Map<String, Object> metadataFilter, final VPromptContext context) {
		return new Lc4jChat(documentSource, metadataFilter, context, chatModel, chatModelStream, tokenizer);
	}

	public static class VServiceOutputParser extends ServiceOutputParser {
		private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]"); // from serviceOutputParser

		@Override
		public String outputFormatInstructions(final Type returnType) {
			if ("io.vertigo.ai.impl.llm.FacetPromptUtil$FacetPromptResult".equals(returnType.getTypeName())) {
				return """
						You must answer strictly in the following JSON format:
						{
						 "selectedFacetValues": {
						  "__facetDefinitionId__": ["__facetValueCode__", "__facetValueCode2__", ...],
						  "__facetDefinitionId2__": ["__facetValueCode3__"],
						  ...
						 }(type: java.util.Map<String, List<String>>),
						 "criteria": (type: string)
						}
						""";
			}
			return super.outputFormatInstructions(returnType);
		}

		@Override
		public Object parse(final Response<AiMessage> response, final Type returnType) {
			final var jsonEngine = Node.getNode().getComponentSpace().resolve(JsonEngine.class);
			final var text = response.content().text();
			try {
				return jsonEngine.fromJson(text, returnType, Collections.emptySet(), Collections.emptySet());
			} catch (final Exception e) {
				try {
					final String jsonBlock = extractJsonBlock(text);
					return jsonEngine.fromJson(jsonBlock, returnType, Collections.emptySet(), Collections.emptySet());
				} catch (final Exception e2) {
					LOG.warn("Error while parsing JSON response from the LLM: {}", text);
					throw new VUserException("The request have not been understood by the system.");
				}
			}
		}

		private String extractJsonBlock(final String text) {
			final Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
			if (matcher.find()) {
				return matcher.group();
			}
			return text;
		}

	}

	@Override
	public VLlmDocumentSource getPersistedDocumentSource() {
		return storagePlugin
				.orElseThrow(() -> new VSystemException("No persisted document source configured"))
				.getDocumentSource();
	}

	@Override
	public VLlmDocumentSource getTemporaryDocumentSource() {
		return new Lc4jInMemoryDocumentSource(embeddingPlugin.getEmbeddingModel());
	}
}
