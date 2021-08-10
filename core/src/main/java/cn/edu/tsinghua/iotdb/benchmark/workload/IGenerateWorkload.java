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

package cn.edu.tsinghua.iotdb.benchmark.workload;

import cn.edu.tsinghua.iotdb.benchmark.workload.ingestion.Batch;
import cn.edu.tsinghua.iotdb.benchmark.workload.schema.DeviceSchema;

/**
 * IGenerateWorkload is a workload using for data which is generated by benchmark
 */
public interface IGenerateWorkload extends IWorkLoad{

  /**
   * Insert one batch into database
   * NOTICE: every row contains data from all sensors
   *
   * @param deviceSchema
   * @param loopIndex
   * @return
   * @throws WorkloadException
   */
  Batch getOneBatch(DeviceSchema deviceSchema, long loopIndex) throws WorkloadException;

  /**
   * Insert one batch into database
   * NOTICE: every row contains data from sensor which index is colIndex
   *
   * @param deviceSchema
   * @param loopIndex
   * @param colIndex
   * @return
   * @throws WorkloadException
   */
  Batch getOneBatch(DeviceSchema deviceSchema, long loopIndex, int colIndex)
      throws WorkloadException;
}
