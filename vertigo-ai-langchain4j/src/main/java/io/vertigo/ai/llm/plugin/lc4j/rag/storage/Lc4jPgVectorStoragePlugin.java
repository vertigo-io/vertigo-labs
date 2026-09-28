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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.sql.DataSource;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.vertigo.ai.llm.model.rag.VLlmDocumentSource;
import io.vertigo.ai.llm.plugin.lc4j.rag.embedding.Lc4jEmbeddingPlugin;
import io.vertigo.core.lang.Assertion;
import io.vertigo.core.lang.VSystemException;
import io.vertigo.core.lang.WrappedException;
import io.vertigo.core.param.ParamValue;
import io.vertigo.database.impl.sql.SqlConnectionProviderPlugin;
import io.vertigo.datastore.entitystore.EntityStoreManager;
import io.vertigo.datastore.filestore.FileStoreManager;
import io.vertigo.datastore.filestore.model.FileInfoURI;

/**
 * Plugin to use PgVector as data store for embeddings.
 *
 * @author skerdudou
 */
public final class Lc4jPgVectorStoragePlugin implements Lc4jStoragePlugin {

	private final DataSource dataSource;
	private final String tableName;
	private final boolean isJsonbStorage;
	private final Lc4jPgVectorDocumentSource documentSource;
	private final EmbeddingModel embeddingModel;

	@Inject
	public Lc4jPgVectorStoragePlugin(
			@ParamValue("dataSpace") final Optional<String> optDataSpace,
			@ParamValue("tableName") final Optional<String> tableNameOpt,
			@ParamValue("metadataColumns") final Optional<String> metadataColumnsString,
			@ParamValue("createTable") final Optional<Boolean> createTable,
			@ParamValue("dropTableFirst") final Optional<Boolean> dropTableFirst,
			final Lc4jEmbeddingPlugin embeddingPlugin,
			final FileStoreManager fileStoreManager,
			final List<SqlConnectionProviderPlugin> sqlConnectionProviderPlugins) {

		Assertion.check()
				.isNotNull(embeddingPlugin)
				.isNotNull(fileStoreManager);
		//---
		final List<String> metadataColumns = metadataColumnsString.map(s -> List.of(s.split(";"))).orElse(Collections.emptyList());

		final var dataSpace = optDataSpace.orElse(EntityStoreManager.MAIN_DATA_SPACE_NAME);
		final var sqlProvider = sqlConnectionProviderPlugins.stream()
				.filter(p -> p.getName().equals(dataSpace))
				.findAny()
				.orElseThrow(() -> new VSystemException("No connection provider found for dataSpace : {0}", dataSpace));
		dataSource = new DatasourceAdapter(sqlProvider);

		embeddingModel = embeddingPlugin.getEmbeddingModel();

		tableName = tableNameOpt.orElse("V_LLM_EMBEDDINGS");
		final var embeddingStoreBuilder = PgVectorEmbeddingStore.datasourceBuilder()
				.datasource(dataSource)
				.table(tableName)
				.dimension(embeddingModel.dimension())
				.createTable(createTable.orElse(Boolean.FALSE))
				.dropTableFirst(dropTableFirst.orElse(Boolean.FALSE));

		if (metadataColumns.isEmpty()) {
			isJsonbStorage = true;
			embeddingStoreBuilder.metadataStorageConfig(DefaultMetadataStorageConfig.builder()
					.storageMode(MetadataStorageMode.COMBINED_JSONB)
					.columnDefinitions(Collections.singletonList("metadata JSONB NULL"))
					.build());
		} else {
			isJsonbStorage = false;
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

	/**
	 * Get all known documents in storage, usefull for demo purpose but should not be used in production.
	 *
	 * @return a list of DocumentInfo
	 */
	public List<VDocumentInfo> getAllDocumentsInfos() {
		final var result = new ArrayList<VDocumentInfo>();
		try (var conn = dataSource.getConnection();
				final var stmt = conn.createStatement();) {
			final ResultSet rs;
			if (isJsonbStorage) {
				rs = stmt.executeQuery("""
						SELECT metadata ->> 'file_urn' as file_urn,
						       metadata ->> 'file_name' as file_name,
						       count(*) as chunk_count
						FROM %s
						group by metadata ->> 'file_urn',
						         metadata ->> 'file_name'
						""".formatted(tableName));
			} else {
				rs = stmt.executeQuery("""
						SELECT file_urn,
						       file_name,
						       count(*) as chunk_count
						FROM %s
						group by file_urn,
						         file_name
						""".formatted(tableName));
			}

			while (rs.next()) {
				final var fileInfoURI = FileInfoURI.fromURN(rs.getString("file_urn"));
				final var fileName = rs.getString("file_name");
				final var chunkCount = rs.getLong("chunk_count");
				result.add(new VDocumentInfo(fileInfoURI, fileName, chunkCount));
			}
		} catch (final SQLException e) {
			throw WrappedException.wrap(e);
		}
		return result;
	}

	public static record VDocumentInfo(FileInfoURI fileInfoURI, String fileName, Long chunkCount) {
	}

	/**
	 * Workaround to use Vertigo connection provider in Lc4j.
	 */
	private static class DatasourceAdapter implements DataSource {

		private final SqlConnectionProviderPlugin sqlConnectionProviderPlugin;

		public DatasourceAdapter(final SqlConnectionProviderPlugin sqlConnectionProviderPlugin) {
			this.sqlConnectionProviderPlugin = sqlConnectionProviderPlugin;
		}

		@Override
		public Connection getConnection() throws SQLException {
			final var jdbcConnection = sqlConnectionProviderPlugin.obtainConnection().getJdbcConnection();
			// Lc4j do not commit and is closing the connection, vertigo disable autocomit (which is desirable).
			// If we want to avoid auto commit, we need to wrap the connection in order to wire it's lifecycle to the VTransactionManager.
			jdbcConnection.setAutoCommit(true);
			return jdbcConnection;
		}

		@Override
		public Connection getConnection(final String username, final String password) throws SQLException {
			return getConnection();
		}

		@Override
		public Logger getParentLogger() throws SQLFeatureNotSupportedException {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T unwrap(final Class<T> iface) throws SQLException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isWrapperFor(final Class<?> iface) throws SQLException {
			throw new UnsupportedOperationException();
		}

		@Override
		public PrintWriter getLogWriter() throws SQLException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLogWriter(final PrintWriter out) throws SQLException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLoginTimeout(final int seconds) throws SQLException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getLoginTimeout() throws SQLException {
			throw new UnsupportedOperationException();
		}

	}
}
