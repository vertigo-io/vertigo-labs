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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.vertigo.ai.llm.LlmPlugin;
import io.vertigo.ai.llm.model.LlmChat;
import io.vertigo.ai.llm.model.VLlmResult;
import io.vertigo.ai.llm.model.VPrompt;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.ai.llm.plugin.lc4j.document.Lc4jDocumentUtil;
import io.vertigo.ai.llm.plugin.lc4j.document.VFileDocumentLoader;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.node.Node;
import io.vertigo.core.param.ParamValue;
import io.vertigo.datastore.filestore.model.VFile;
import io.vertigo.vega.engines.webservice.json.JsonEngine;

/**
 * Defines llm plugin using OpenAi with langchain4j.
 *
 * @author skerdudou
 */
public final class Lc4jPlugin implements LlmPlugin {

	private final ChatLanguageModel chatModel;
	//private final StreamingChatLanguageModel chatModelStream;

	private final ServiceOutputParser serviceOutputParser = new VServiceOutputParser();

	@Inject
	public Lc4jPlugin(
			@ParamValue("apiKey") final String apiKey) {

		Assertion.check()
				.isNotBlank(apiKey); // langchain4j can use "demo" api key with a 5000 tokens limit (with langchain4j server acting as a proxy)
		//---
		chatModel = OpenAiChatModel.builder()
				//.baseUrl("http://albert.dev.klee.lan.net:8000/v1")
				.apiKey(apiKey)
				//.modelName("turbo")
				.modelName("gpt-4o-mini")
				.temperature(0d)
				.build();

		//		chatModelStream = OpenAiStreamingChatModel.builder()
		//				.apiKey(apiKey)
		//				.modelName("gpt-4o-mini")
		//				.temperature(0d)
		//				.build();

	}

	@Override
	public VLlmResult askOnFiles(final VPrompt prompt, final Stream<VFile> files) {
		final List<Document> documents = files
				.map(VFileDocumentLoader::loadDocument)
				.toList();

		final var assistantBuilder = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel);

		final var context = prompt.getContext();
		if (context.getPersona() != null) {
			assistantBuilder.systemMessageProvider(s -> Lc4jUtils.getSystemMessageFromPersona(context.getPersona()).text());
		}
		if (!documents.isEmpty()) {
			assistantBuilder.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)); // it should have access to our documents
		}
		final Assistant assistant = assistantBuilder.build();

		Result<String> llmResponse;
		try {
			llmResponse = assistant.rawAnswer(prompt.getInstructions());
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		return new Lc4jResult(llmResponse);
	}

	@Override
	public <T> T ask(final VPrompt prompt, final Class<T> clazz) {
		final var chatMessages = new ArrayList<ChatMessage>();
		final var context = prompt.getContext();

		if (context.getPersona() != null) {
			chatMessages.add(Lc4jUtils.getSystemMessageFromPersona(context.getPersona()));
		}

		// append additional instructions as in dev.langchain4j.service.DefaultAiServices
		// don't know why it is not done in the system message
		final var additionalInstructions = serviceOutputParser.outputFormatInstructions(clazz);
		chatMessages.add(UserMessage.from(prompt.getInstructions() + "\n" + additionalInstructions));

		final var llmResponse = chatModel.generate(chatMessages);
		return (T) serviceOutputParser.parse(llmResponse, clazz);
	}

	@Override
	public LlmChat newChat(final Stream<VFile> files, final VPromptContext context) {
		return new Lc4jChat(files.toList(), chatModel, context);
	}

	public static class VServiceOutputParser extends ServiceOutputParser {
		private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]"); // from serviceOutputParser

		@Override
		public String outputFormatInstructions(final Type returnType) {
			if ("io.vertigo.ai.impl.llm.FacetPromptUtil$FacetPromptResult".equals(returnType.getTypeName())) {
				return """
						You must answer strictly in the following JSON format: {
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
				return jsonEngine.fromJson(text, returnType);
			} catch (final Exception e) {
				final String jsonBlock = extractJsonBlock(text);
				return jsonEngine.fromJson(jsonBlock, returnType);
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
}
