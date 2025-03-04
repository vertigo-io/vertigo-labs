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
package io.vertigo.ai.llm.plugin.lc4j.rag.embedding;

import javax.inject.Inject;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;
import io.vertigo.core.param.ParamValue;

/**
 * Plugin to use OpenAi as embedding model
 *
 * @author skerdudou
 */
public final class Lc4jOpenAiEmbeddingPlugin implements Lc4jEmbeddingPlugin {

	private final EmbeddingModel embeddingModel;

	@Inject
	public Lc4jOpenAiEmbeddingPlugin(
			@ParamValue("apiKey") final String apiKey) {

		embeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL)
				.build();
	}

	@Override
	public EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}
}
