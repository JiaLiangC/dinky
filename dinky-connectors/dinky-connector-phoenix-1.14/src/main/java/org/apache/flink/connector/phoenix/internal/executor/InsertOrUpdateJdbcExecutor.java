/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.flink.connector.phoenix.internal.executor;

import static org.apache.flink.util.Preconditions.checkNotNull;

import org.apache.flink.annotation.Internal;
import org.apache.flink.connector.phoenix.JdbcStatementBuilder;
import org.apache.flink.connector.phoenix.table.PhoenixUpsertTableSink;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JdbcBatchStatementExecutor} that provides upsert semantics by updating row if it exists
 * and inserting otherwise. Used in Table API.
 *
 * @deprecated This has been replaced with {@link TableInsertOrUpdateStatementExecutor}, will remove
 *     this once {@link PhoenixUpsertTableSink} is removed.
 */
@Internal
public final class InsertOrUpdateJdbcExecutor<R, K, V> implements JdbcBatchStatementExecutor<R> {

    private static final Logger LOG = LoggerFactory.getLogger(InsertOrUpdateJdbcExecutor.class);

    private final String existSQL;
    private final String insertSQL;
    private final String updateSQL;

    private final JdbcStatementBuilder<K> existSetter;
    private final JdbcStatementBuilder<V> insertSetter;
    private final JdbcStatementBuilder<V> updateSetter;

    private final Function<R, K> keyExtractor;
    private final Function<R, V> valueMapper;

    private final Map<K, V> batch;

    private transient PreparedStatement existStatement;
    private transient PreparedStatement insertStatement;
    private transient PreparedStatement updateStatement;

    public InsertOrUpdateJdbcExecutor(
            @Nonnull String existSQL,
            @Nonnull String insertSQL,
            @Nonnull String updateSQL,
            @Nonnull JdbcStatementBuilder<K> existSetter,
            @Nonnull JdbcStatementBuilder<V> insertSetter,
            @Nonnull JdbcStatementBuilder<V> updateSetter,
            @Nonnull Function<R, K> keyExtractor,
            @Nonnull Function<R, V> valueExtractor) {
        this.existSQL = checkNotNull(existSQL);
        this.updateSQL = checkNotNull(updateSQL);
        this.existSetter = checkNotNull(existSetter);
        this.insertSQL = checkNotNull(insertSQL);
        this.insertSetter = checkNotNull(insertSetter);
        this.updateSetter = checkNotNull(updateSetter);
        this.keyExtractor = checkNotNull(keyExtractor);
        this.valueMapper = checkNotNull(valueExtractor);
        this.batch = new HashMap<>();
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        existStatement = connection.prepareStatement(existSQL);
        insertStatement = connection.prepareStatement(insertSQL);
        updateStatement = connection.prepareStatement(updateSQL);
    }

    @Override
    public void addToBatch(R record) {
        batch.put(keyExtractor.apply(record), valueMapper.apply(record));
    }

    @Override
    public void executeBatch(Connection conn) throws SQLException {
        if (!batch.isEmpty()) {
            for (Map.Entry<K, V> entry : batch.entrySet()) {
                processOneRowInBatch(entry.getKey(), entry.getValue());
            }
            conn.commit();
            batch.clear();
        }
    }

    private void processOneRowInBatch(K pk, V row) throws SQLException {
        if (exist(pk)) {
            updateSetter.accept(updateStatement, row);
            updateStatement.executeUpdate();
        } else {
            insertSetter.accept(insertStatement, row);
            insertStatement.executeUpdate();
        }
    }

    private boolean exist(K pk) throws SQLException {
        existSetter.accept(existStatement, pk);
        try (ResultSet resultSet = existStatement.executeQuery()) {
            return resultSet.next();
        }
    }

    @Override
    public void closeStatements() throws SQLException {
        for (PreparedStatement s : Arrays.asList(existStatement, insertStatement, updateStatement)) {
            if (s != null) {
                s.close();
            }
        }
    }
}
