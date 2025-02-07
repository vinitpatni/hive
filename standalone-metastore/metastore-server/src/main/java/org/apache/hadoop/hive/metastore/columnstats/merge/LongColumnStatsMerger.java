/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hadoop.hive.metastore.columnstats.merge;

import org.apache.hadoop.hive.common.histogram.KllHistogramEstimator;
import org.apache.hadoop.hive.common.ndv.NumDistinctValueEstimator;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsObj;
import org.apache.hadoop.hive.metastore.columnstats.cache.LongColumnStatsDataInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.hadoop.hive.metastore.columnstats.ColumnsStatsUtils.longInspectorFromStats;

public class LongColumnStatsMerger extends ColumnStatsMerger {

  private static final Logger LOG = LoggerFactory.getLogger(LongColumnStatsMerger.class);

  @Override
  public void merge(ColumnStatisticsObj aggregateColStats, ColumnStatisticsObj newColStats) {
    LOG.debug("Merging statistics: [aggregateColStats:{}, newColStats: {}]", aggregateColStats, newColStats);

    LongColumnStatsDataInspector aggregateData = longInspectorFromStats(aggregateColStats);
    LongColumnStatsDataInspector newData = longInspectorFromStats(newColStats);
    setLowValue(aggregateData, newData);
    setHighValue(aggregateData, newData);
    aggregateData.setNumNulls(aggregateData.getNumNulls() + newData.getNumNulls());
    if (aggregateData.getNdvEstimator() == null || newData.getNdvEstimator() == null) {
      aggregateData.setNumDVs(Math.max(aggregateData.getNumDVs(), newData.getNumDVs()));
    } else {
      NumDistinctValueEstimator oldEst = aggregateData.getNdvEstimator();
      NumDistinctValueEstimator newEst = newData.getNdvEstimator();
      final long ndv;
      if (oldEst.canMerge(newEst)) {
        oldEst.mergeEstimators(newEst);
        ndv = oldEst.estimateNumDistinctValues();
        aggregateData.setNdvEstimator(oldEst);
      } else {
        ndv = Math.max(aggregateData.getNumDVs(), newData.getNumDVs());
      }
      LOG.debug("Use bitvector to merge column {}'s ndvs of {} and {} to be {}", aggregateColStats.getColName(),
          aggregateData.getNumDVs(), newData.getNumDVs(), ndv);
      aggregateData.setNumDVs(ndv);
    }

    KllHistogramEstimator oldEst = aggregateData.getHistogramEstimator();
    KllHistogramEstimator newEst = newData.getHistogramEstimator();
    aggregateData.setHistogramEstimator(mergeHistogramEstimator(aggregateColStats.getColName(), oldEst, newEst));

    aggregateColStats.getStatsData().setLongStats(aggregateData);
  }

  public void setLowValue(LongColumnStatsDataInspector aggregateData, LongColumnStatsDataInspector newData) {
    final long lowValue;

    if (aggregateData.isSetLowValue() && newData.isSetLowValue()) {
      lowValue = Math.min(aggregateData.getLowValue(), newData.getLowValue());
    } else if (aggregateData.isSetLowValue()) {
      lowValue = aggregateData.getLowValue();
    } else if (newData.isSetLowValue()) {
      lowValue = newData.getLowValue();
    } else {
      return;
    }

    aggregateData.setLowValue(lowValue);
  }

  public void setHighValue(LongColumnStatsDataInspector aggregateData, LongColumnStatsDataInspector newData) {
    final long highValue;

    if (aggregateData.isSetHighValue() && newData.isSetHighValue()) {
      highValue = Math.max(aggregateData.getHighValue(), newData.getHighValue());
    } else if (aggregateData.isSetHighValue()) {
      highValue = aggregateData.getHighValue();
    } else if (newData.isSetHighValue()) {
      highValue = newData.getHighValue();
    } else {
      return;
    }

    aggregateData.setHighValue(highValue);
  }
}
