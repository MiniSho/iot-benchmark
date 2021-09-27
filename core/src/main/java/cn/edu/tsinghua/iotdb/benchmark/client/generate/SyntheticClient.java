/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package cn.edu.tsinghua.iotdb.benchmark.client.generate;

import cn.edu.tsinghua.iotdb.benchmark.client.operation.Operation;
import cn.edu.tsinghua.iotdb.benchmark.client.operation.OperationController;
import cn.edu.tsinghua.iotdb.benchmark.conf.Constants;
import cn.edu.tsinghua.iotdb.benchmark.exception.DBConnectException;
import cn.edu.tsinghua.iotdb.benchmark.measurement.Status;
import cn.edu.tsinghua.iotdb.benchmark.schema.DeviceSchema;
import cn.edu.tsinghua.iotdb.benchmark.schema.enums.Type;
import cn.edu.tsinghua.iotdb.benchmark.tsdb.DBWrapper;
import cn.edu.tsinghua.iotdb.benchmark.workload.SyntheticDataWorkload;
import cn.edu.tsinghua.iotdb.benchmark.workload.WorkloadException;
import cn.edu.tsinghua.iotdb.benchmark.workload.ingestion.Batch;
import cn.edu.tsinghua.iotdb.benchmark.workload.query.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class SyntheticClient extends GenerateBaseClient {

  /** Control operation according to OPERATION_PROPORTION */
  private final OperationController operationController;

  private static final double THRESHOLD = 0.0000001D;

  public SyntheticClient(int id, CountDownLatch countDownLatch, CyclicBarrier barrier) {
    super(id, countDownLatch, barrier, new SyntheticDataWorkload(id));
    this.operationController = new OperationController(id);
  }

  /**
   * Do Operations
   *
   * @param actualDeviceFloor
   */
  @Override
  protected void doOperations(int actualDeviceFloor) {
    long start = 0;
    for (loopIndex = 0; loopIndex < config.getLOOP(); loopIndex++) {
      Operation operation = operationController.getNextOperationType();
      if (config.getOP_INTERVAL() > 0) {
        start = System.currentTimeMillis();
      }
      if (operation == Operation.INGESTION) {
        if (!ingestionOperation(actualDeviceFloor)) {
          break;
        }
      } else {
        try {
          List<Status> statuses = new ArrayList<>();
          Query query = getQuery(operation);
          for (DBWrapper dbWrapper : dbWrappers) {
            Status status = null;
            switch (operation) {
              case PRECISE_QUERY:
                status = dbWrapper.preciseQuery((PreciseQuery) query);
                break;
              case RANGE_QUERY:
                status = dbWrapper.rangeQuery((RangeQuery) query);
                break;
              case VALUE_RANGE_QUERY:
                status = dbWrapper.valueRangeQuery((ValueRangeQuery) query);
                break;
              case AGG_RANGE_QUERY:
                status = dbWrapper.aggRangeQuery((AggRangeQuery) query);
                break;
              case AGG_VALUE_QUERY:
                status = dbWrapper.aggValueQuery((AggValueQuery) query);
                break;
              case AGG_RANGE_VALUE_QUERY:
                status = dbWrapper.aggRangeValueQuery((AggRangeValueQuery) query);
                break;
              case GROUP_BY_QUERY:
                status = dbWrapper.groupByQuery((GroupByQuery) query);
                break;
              case LATEST_POINT_QUERY:
                status = dbWrapper.latestPointQuery((LatestPointQuery) query);
                break;
              case RANGE_QUERY_ORDER_BY_TIME_DESC:
                status = dbWrapper.rangeQueryOrderByDesc((RangeQuery) query);
                break;
              case VALUE_RANGE_QUERY_ORDER_BY_TIME_DESC:
                status = dbWrapper.valueRangeQueryOrderByDesc((ValueRangeQuery) query);
                break;
              default:
                LOGGER.error("Unsupported operation type {}", operation);
            }
            statuses.add(status);
          }
          if (config.isIS_COMPARISON() && statuses.size() >= 2) {
            Status status1 = statuses.get(0);
            Status status2 = statuses.get(1);
            boolean isError = false;
            if (status1 != null
                && status2 != null
                && status1.getRecords() != null
                && status2.getRecords() != null) {
              int point1 = status1.getQueryResultPointNum();
              int point2 = status2.getQueryResultPointNum();
              if (point1 != point2) {
                isError = true;
              } else if (point1 != 0) {
                List<List<Object>> records1 = status1.getRecords();
                List<List<Object>> records2 = status2.getRecords();
                boolean needSort = true;
                if (query instanceof RangeQuery) {
                  if (((RangeQuery) query).isDesc()) {
                    needSort = false;
                  }
                }
                if (needSort) {
                  Collections.sort(records1, new RecordComparator());
                  Collections.sort(records2, new RecordComparator());
                }
                // 顺序比较
                for (int i = 0; i < point1; i++) {
                  List<Object> record1 = records1.get(i);
                  List<Object> record2 = records2.get(i);
                  for (int j = 0; j < record1.size(); j++) {
                    String r1 = String.valueOf(record1.get(j));
                    String r2 = String.valueOf(record2.get(j));
                    if (!r1.equals(r2)) {
                      isError = true;
                    }
                  }
                }
              }
            }
            if (isError) {
              doErrorLog(query.getClass().getSimpleName(), status1, status2);
            }
          }
        } catch (Exception e) {
          LOGGER.error("Failed to do " + operation.getName() + " query because ", e);
        }
      }
      if (config.getOP_INTERVAL() > 0) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed < config.getOP_INTERVAL()) {
          try {
            Thread.sleep(config.getOP_INTERVAL() - elapsed);
          } catch (InterruptedException e) {
            LOGGER.error("Wait for next operation failed because ", e);
          }
        }
      }
    }
  }

  private void doErrorLog(String queryName, Status status1, Status status2) {
    LOGGER.error(
        queryName
            + " DB1 point: "
            + status1.getQueryResultPointNum()
            + " and DB2 point: "
            + status2.getQueryResultPointNum()
            + "\n"
            + config.getDbConfig().getDB_SWITCH()
            + ":"
            + status1.getSql()
            + "\n"
            + config.getANOTHER_DBConfig().getDB_SWITCH()
            + ":"
            + status2.getSql());
  }

  private Query getQuery(Operation operation) throws WorkloadException {
    Query query = null;
    switch (operation) {
      case PRECISE_QUERY:
        query = syntheticWorkload.getPreciseQuery();
        break;
      case RANGE_QUERY:
        query = syntheticWorkload.getRangeQuery();
        break;
      case VALUE_RANGE_QUERY:
        query = syntheticWorkload.getValueRangeQuery();
        break;
      case AGG_RANGE_QUERY:
        query = syntheticWorkload.getAggRangeQuery();
        break;
      case AGG_VALUE_QUERY:
        query = syntheticWorkload.getAggValueQuery();
        break;
      case AGG_RANGE_VALUE_QUERY:
        query = syntheticWorkload.getAggRangeValueQuery();
        break;
      case GROUP_BY_QUERY:
        query = syntheticWorkload.getGroupByQuery();
        break;
      case LATEST_POINT_QUERY:
        query = syntheticWorkload.getLatestPointQuery();
        break;
      case RANGE_QUERY_ORDER_BY_TIME_DESC:
        query = syntheticWorkload.getRangeQuery();
        break;
      case VALUE_RANGE_QUERY_ORDER_BY_TIME_DESC:
        query = syntheticWorkload.getValueRangeQuery();
        break;
      default:
        LOGGER.error("Unsupported operation type {}", operation);
        query = syntheticWorkload.getPreciseQuery();
    }
    return query;
  }

  /**
   * Do Ingestion Operation
   *
   * @param actualDeviceFloor @Return when connect failed return false
   */
  private boolean ingestionOperation(int actualDeviceFloor) {
    try {
      if (config.isIS_CLIENT_BIND()) {
        List<DeviceSchema> schemas = baseDataSchema.getThreadDeviceSchema(clientThreadId);
        if (config.isIS_SENSOR_TS_ALIGNMENT()) {
          // IS_CLIENT_BIND == true && IS_SENSOR_TS_ALIGNMENT = true
          for (DeviceSchema deviceSchema : schemas) {
            if (deviceSchema.getDeviceId() <= actualDeviceFloor) {
              Batch batch = syntheticWorkload.getOneBatch(deviceSchema, insertLoopIndex);
              for (DBWrapper dbWrapper : dbWrappers) {
                dbWrapper.insertOneBatch(batch);
              }
            }
          }
          insertLoopIndex++;
        } else {
          // IS_CLIENT_BIND == true && IS_SENSOR_IS_ALIGNMENT = false
          DeviceSchema sensorSchema = null;
          List<String> sensorList = new ArrayList<String>();
          for (DeviceSchema deviceSchema : schemas) {
            if (deviceSchema.getDeviceId() <= actualDeviceFloor) {
              for (String sensor : deviceSchema.getSensors()) {
                int colIndex = Integer.parseInt(sensor.replace(Constants.SENSOR_NAME_PREFIX, ""));
                sensorList = new ArrayList<String>();
                sensorList.add(sensor);
                sensorSchema = (DeviceSchema) deviceSchema.clone();
                sensorSchema.setSensors(sensorList);
                Batch batch =
                    syntheticWorkload.getOneBatch(sensorSchema, insertLoopIndex, colIndex);
                batch.setColIndex(colIndex);
                Type colType = baseDataSchema.getSensorType(deviceSchema.getDevice(), sensor);
                batch.setColType(colType);
                for (DBWrapper dbWrapper : dbWrappers) {
                  dbWrapper.insertOneSensorBatch(batch);
                }
                insertLoopIndex++;
              }
            }
          }
        }
      } else {
        // IS_CLIENT_BIND = false
        Batch batch = singletonWorkload.getOneBatch();
        if (batch.getDeviceSchema().getDeviceId() <= actualDeviceFloor) {
          for (DBWrapper dbWrapper : dbWrappers) {
            dbWrapper.insertOneBatch(batch);
          }
        }
      }
    } catch (DBConnectException e) {
      LOGGER.error("Failed to insert one batch data because ", e);
      return false;
    } catch (Exception e) {
      LOGGER.error("Failed to insert one batch data because ", e);
    }
    return true;
  }
}
