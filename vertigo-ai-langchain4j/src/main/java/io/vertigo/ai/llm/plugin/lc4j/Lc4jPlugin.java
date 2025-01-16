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

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.vertigo.ai.impl.llm.VPrompt;
import io.vertigo.ai.llm.LlmPlugin;
import io.vertigo.ai.llm.plugin.lc4j.document.Lc4jDocumentUtil;
import io.vertigo.ai.llm.plugin.lc4j.document.VFileDocumentLoader;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.datastore.filestore.model.VFile;

/**
 * Defines llm plugin using OpenAi with langchain4j.
 *
 * @author skerdudou
 */
public final class Lc4jPlugin implements LlmPlugin {

	private final ChatLanguageModel chatModel;

	private final Parser parser;
	private final HtmlRenderer renderer;

	@Inject
	public Lc4jPlugin(
			@ParamValue("apiKey") final String apiKey) {

		Assertion.check()
				.isNotBlank(apiKey); // langchain4j can use "demo" api key with a 5000 tokens limit (with langchain4j server acting as a proxy)
		//---
		chatModel = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName("gpt-4o-mini")
				.build();

		// for markdown parsing
		parser = Parser.builder().build();
		renderer = HtmlRenderer.builder().omitSingleParagraphP(true).build();
	}

	@Override
	public String promptOnFiles(final VPrompt prompt, final Stream<VFile> files) {
		final List<Document> documents = files
				.map(VFileDocumentLoader::loadDocument)
				.toList();

		final Assistant assistant = AiServices.builder(Assistant.class)
				.chatLanguageModel(chatModel) // it should use OpenAI LLM
				//.chatMemory(MessageWindowChatMemory.withMaxMessages(10)) // it should remember 10 latest messages
				.contentRetriever(Lc4jDocumentUtil.createContentRetriever(documents)) // it should have access to our documents
				.build();
		String llmResponse;
		try {
			llmResponse = assistant.answer(prompt.instructions());
		} catch (final Exception e) {
			throw new VSystemException(e, e.getMessage());
		}

		final Node document = parser.parse(llmResponse);
		return renderer.render(document);
	}

}
