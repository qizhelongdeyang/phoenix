/*
 * Copyright 2014 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.index;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.Stoppable;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;

import com.google.common.collect.Multimap;
import org.apache.hbase.index.table.HTableInterfaceReference;
import org.apache.hbase.index.write.KillServerOnFailurePolicy;
import org.apache.phoenix.coprocessor.MetaDataProtocol;
import org.apache.phoenix.coprocessor.MetaDataProtocol.MetaDataMutationResult;
import org.apache.phoenix.coprocessor.MetaDataProtocol.MutationCode;
import org.apache.phoenix.jdbc.PhoenixDatabaseMetaData;
import org.apache.phoenix.schema.PIndexState;
import org.apache.phoenix.util.SchemaUtil;

/**
 * 
 * Handler called in the event that index updates cannot be written to their
 * region server. First attempts to disable the index and failing that falls
 * back to the default behavior of killing the region server.
 *
 * TODO: use delegate pattern instead
 * 
 * 
 * @since 2.1
 */
public class PhoenixIndexFailurePolicy extends  KillServerOnFailurePolicy {
    private static final Log LOG = LogFactory.getLog(PhoenixIndexFailurePolicy.class);
    private RegionCoprocessorEnvironment env;

    public PhoenixIndexFailurePolicy() {
    }

    @Override
    public void setup(Stoppable parent, RegionCoprocessorEnvironment env) {
      super.setup(parent, env);
      this.env = env;
    }

    @Override
    public void handleFailure(Multimap<HTableInterfaceReference, Mutation> attempted, Exception cause) throws IOException {
        Set<HTableInterfaceReference> refs = attempted.asMap().keySet();
        StringBuilder buf = new StringBuilder("Disabled index" + (refs.size() > 1 ? "es " : " "));
        try {
            for (HTableInterfaceReference ref : refs) {
                // Disable the index by using the updateIndexState method of MetaDataProtocol end point coprocessor.
                String indexTableName = ref.getTableName();
                byte[] indexTableKey = SchemaUtil.getTableKeyFromFullName(indexTableName);
                HTableInterface systemTable = env.getTable(PhoenixDatabaseMetaData.TYPE_TABLE_NAME_BYTES);
                MetaDataProtocol mdProxy = systemTable.coprocessorProxy(MetaDataProtocol.class, indexTableKey);
                // Mimic the Put that gets generated by the client on an update of the index state
                Put put = new Put(indexTableKey);
                put.add(PhoenixDatabaseMetaData.TABLE_FAMILY_BYTES, PhoenixDatabaseMetaData.INDEX_STATE_BYTES, PIndexState.DISABLE.getSerializedBytes());
                List<Mutation> tableMetadata = Collections.<Mutation>singletonList(put);
                MetaDataMutationResult result = mdProxy.updateIndexState(tableMetadata);
                if (result.getMutationCode() != MutationCode.TABLE_ALREADY_EXISTS) {
                    LOG.warn("Attempt to disable index " + indexTableName + " failed with code = " + result.getMutationCode() + ". Will use default failure policy instead.");
                    super.handleFailure(attempted, cause);
                }
                LOG.info("Successfully disabled index " + indexTableName);
                buf.append(indexTableName);
                buf.append(',');
            }
            buf.setLength(buf.length()-1);
            buf.append(" due to an exception while writing updates");
        } catch (Throwable t) {
            super.handleFailure(attempted, cause);
        }
        throw new DoNotRetryIOException(buf.toString(), cause);
    }

}
