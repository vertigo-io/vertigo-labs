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

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import java.util.Collection;

import jakarta.inject.Inject;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import io.vertigo.core.lang.VSystemException;

/**
 * Plugin to use autowired embedding model (from dependencies)
 *
 * @author skerdudou
 */
public final class Lc4jClasspathEmbeddingPlugin implements Lc4jEmbeddingPlugin {

	private final EmbeddingModel embeddingModel;

	@Inject
	public Lc4jClasspathEmbeddingPlugin() {
		embeddingModel = loadEmbeddingModel();
	}

	@Override
	public EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}

	// from Lc4j source code
	private static EmbeddingModel loadEmbeddingModel() {
		final Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
		if (factories.size() > 1) {
			throw new VSystemException("Conflict: multiple langchain4j embedding models have been found in the classpath. Please explicitly specify the one you wish to use.");
		}

		for (final EmbeddingModelFactory factory : factories) {
			final EmbeddingModel embeddingModel = factory.create();
			//log.debug("Loaded the following embedding model through SPI: {}", embeddingModel);
			return embeddingModel;
		}

		throw new VSystemException("No langchain4j embedding model found in the classpath. Please add one.");
	}
}
