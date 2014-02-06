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
package org.apache.phoenix.expression.aggregator;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;

import org.apache.phoenix.schema.ColumnModifier;
import org.apache.phoenix.schema.PDataType;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.util.SizedUtil;

public class DoubleSumAggregator extends BaseAggregator {
    
    private double sum = 0;
    private byte[] buffer;

    public DoubleSumAggregator(ColumnModifier columnModifier) {
        super(columnModifier);
    }
    
    protected PDataType getInputDataType() {
        return PDataType.DOUBLE;
    }
    
    private void initBuffer() {
        buffer = new byte[getDataType().getByteSize()];
    }

    @Override
    public void aggregate(Tuple tuple, ImmutableBytesWritable ptr) {
        double value = getInputDataType().getCodec().decodeDouble(ptr, columnModifier);
        sum += value;
        if (buffer == null) {
            initBuffer();
        }
    }

    @Override
    public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
        if (buffer == null) {
            if (isNullable()) {
                return false;
            }
            initBuffer();
        }
        ptr.set(buffer);
        getDataType().getCodec().encodeDouble(sum, ptr);
        return true;
    }

    @Override
    public PDataType getDataType() {
        return PDataType.DOUBLE;
    }
    
    @Override
    public String toString() {
        return "SUM [sum=" + sum + "]";
    }
    
    @Override
    public void reset() {
        sum = 0;
        buffer = null;
        super.reset();
    }
    
    @Override
    public int getSize() {
        return super.getSize() + SizedUtil.LONG_SIZE + SizedUtil.ARRAY_SIZE + getDataType().getByteSize();
    }

}
