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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.param.ParamValue;

/**
 * Plugin to use Ollama as embedding model
 *
 * @author skerdudou
 */
public final class Lc4jOllamaEmbeddingPlugin implements Lc4jEmbeddingPlugin {

	private final EmbeddingModel embeddingModel;

	@Inject
	public Lc4jOllamaEmbeddingPlugin(
			@ParamValue("url") final String url,
			@ParamValue("modelName") final Optional<String> modelNameOpt,
			@ParamValue("apiKey") final Optional<String> apiKeyOpt) {

		Assertion.check()
				.isNotBlank(url);
		// --
		// OpenWebUi uses "Authorization: Bearer YOUR_API_KEY" header to authenticate requests
		final var customHeaders = apiKeyOpt
				.map(key -> Map.of("Authorization", "Bearer " + key))
				.orElseGet(Collections::emptyMap);

		embeddingModel = OllamaEmbeddingModel.builder()
				.baseUrl(url)
				.modelName(modelNameOpt.orElse("qwen2.5:7b"))
				.customHeaders(customHeaders)
				.build();
	}

	@Override
	public EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}
}
