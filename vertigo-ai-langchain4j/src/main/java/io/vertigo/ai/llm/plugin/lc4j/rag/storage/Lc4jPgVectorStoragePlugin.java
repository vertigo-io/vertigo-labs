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
package io.vertigo.ai.llm.plugin.lc4j.rag.storage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.sql.DataSource;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.embedding.Lc4jEmbeddingPlugin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.datastore.filestore.FileStoreManager;

/**
 * Plugin to use PgVector as data store for embeddings.
 *
 * @author skerdudou
 */
public final class Lc4jPgVectorStoragePlugin implements Lc4jStoragePlugin {

	private final Lc4jPgVectorDocumentSource documentSource;
	private final EmbeddingModel embeddingModel;

	@Inject
	public Lc4jPgVectorStoragePlugin(
			@ParamValue("source") final String source,
			@ParamValue("tableName") final Optional<String> tableName,
			@ParamValue("metadataColumns") final Optional<String> metadataColumnsString,
			@ParamValue("createTable") final Optional<Boolean> createTable,
			@ParamValue("dropTableFirst") final Optional<Boolean> dropTableFirst,
			final Lc4jEmbeddingPlugin embeddingPlugin,
			final FileStoreManager fileStoreManager) {

		Assertion.check()
				.isNotBlank(source)
				.isNotNull(embeddingPlugin)
				.isNotNull(fileStoreManager);
		//---
		final List<String> metadataColumns = metadataColumnsString.map(s -> List.of(s.split(";"))).orElse(Collections.emptyList());
		DataSource dataSource;
		try {
			final javax.naming.Context context = new javax.naming.InitialContext();
			dataSource = (DataSource) context.lookup(source);
		} catch (final NamingException e) {
			throw WrappedException.wrap(e, "Can't obtain DataSource : {0}", source);
		}

		embeddingModel = embeddingPlugin.getEmbeddingModel();

		final var embeddingStoreBuilder = PgVectorEmbeddingStore.datasourceBuilder()
				.datasource(dataSource)
				.table(tableName.orElse("V_LLM_EMBEDDINGS"))
				.dimension(embeddingModel.dimension())
				.createTable(createTable.orElse(Boolean.FALSE))
				.dropTableFirst(dropTableFirst.orElse(Boolean.FALSE));

		if (metadataColumns.isEmpty()) {
			embeddingStoreBuilder.metadataStorageConfig(DefaultMetadataStorageConfig.builder()
					.storageMode(MetadataStorageMode.COMBINED_JSONB)
					.columnDefinitions(Collections.singletonList("metadata JSONB NULL"))
					.build());
		} else {
			embeddingStoreBuilder.metadataStorageConfig(DefaultMetadataStorageConfig.builder()
					.storageMode(MetadataStorageMode.COLUMN_PER_KEY)
					.columnDefinitions(metadataColumns)
					.build());
		}

		final PgVectorEmbeddingStore embeddingStore = embeddingStoreBuilder.build();

		documentSource = new Lc4jPgVectorDocumentSource(fileStoreManager, embeddingStore, embeddingModel);
	}

	@Override
	public VLlmDocumentSource getDocumentSource() {
		return documentSource;
	}
}
