// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.storage.datastore.util;

import com.linbit.linstor.api.ApiClient;
import com.linbit.linstor.api.ApiException;
import com.linbit.linstor.api.Configuration;
import com.linbit.linstor.api.DevelopersApi;
import com.linbit.linstor.api.model.ApiCallRc;
import com.linbit.linstor.api.model.ApiCallRcList;
import com.linbit.linstor.api.model.ProviderKind;
import com.linbit.linstor.api.model.Resource;
import com.linbit.linstor.api.model.ResourceGroup;
import com.linbit.linstor.api.model.ResourceWithVolumes;
import com.linbit.linstor.api.model.StoragePool;

import java.util.Collections;
import java.util.List;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.log4j.Logger;

public class LinstorUtil {
    private static final Logger s_logger = Logger.getLogger(LinstorUtil.class);

    public static final String RSC_PREFIX = "cs-";
    public static final String RSC_GROUP = "resourceGroup";

    public static final String TEMP_VOLUME_ID = "tempVolumeId";

    public static final String CLUSTER_DEFAULT_MIN_IOPS = "clusterDefaultMinIops";
    public static final String CLUSTER_DEFAULT_MAX_IOPS = "clusterDefaultMaxIops";

    public static DevelopersApi getLinstorAPI(String linstorUrl) {
        ApiClient client = Configuration.getDefaultApiClient();
        client.setBasePath(linstorUrl);
        return new DevelopersApi(client);
    }

    public static String getBestErrorMessage(ApiCallRcList answers) {
        return answers != null && !answers.isEmpty() ?
                answers.stream()
                        .filter(ApiCallRc::isError)
                        .findFirst()
                        .map(ApiCallRc::getMessage)
                        .orElse((answers.get(0)).getMessage()) : null;
    }

    public static long getCapacityBytes(String linstorUrl, String rscGroupName) {
        DevelopersApi linstorApi = getLinstorAPI(linstorUrl);
        try {
            List<ResourceGroup> rscGrps = linstorApi.resourceGroupList(
                Collections.singletonList(rscGroupName),
                null,
                null,
                null);

            if (rscGrps.isEmpty()) {
                final String errMsg = String.format("Linstor: Resource group '%s' not found", rscGroupName);
                s_logger.error(errMsg);
                throw new CloudRuntimeException(errMsg);
            }

            List<StoragePool> storagePools = linstorApi.viewStoragePools(
                Collections.emptyList(),
                rscGrps.get(0).getSelectFilter().getStoragePoolList(),
                null,
                null,
                null
            );

            return storagePools.stream()
                .filter(sp -> sp.getProviderKind() != ProviderKind.DISKLESS)
                .mapToLong(sp -> sp.getTotalCapacity() != null ? sp.getTotalCapacity() : 0L)
                .sum() * 1024;  // linstor uses kiB
        } catch (ApiException apiEx) {
            s_logger.error(apiEx.getMessage());
            throw new CloudRuntimeException(apiEx);
        }
    }

    /**
     * Check if any resource of the given name is InUse on any host.
     *
     * @param api developer api object to use
     * @param rscName resource name to check in use state.
     * @return True if a resource found that is in use(primary) state, else false.
     * @throws ApiException forwards api errors
     */
    public static boolean isResourceInUse(DevelopersApi api, String rscName) throws ApiException {
        List<Resource> rscs = api.resourceList(rscName, null, null);
        if (rscs != null) {
            return rscs.stream()
                    .anyMatch(rsc -> rsc.getState() != null && Boolean.TRUE.equals(rsc.getState().isInUse()));
        }
        s_logger.error("isResourceInUse: null returned from resourceList");
        return false;
    }

    /**
     * Try to get the device path for the given resource name.
     * This could be made a bit more direct after java-linstor api is fixed for layer data subtypes.
     * @param api developer api object to use
     * @param rscName resource name to get the device path
     * @return The device path of the resource.
     * @throws ApiException if Linstor API call failed.
     * @throws CloudRuntimeException if no device path could be found.
     */
    public static String getDevicePath(DevelopersApi api, String rscName) throws ApiException, CloudRuntimeException {
        List<ResourceWithVolumes> resources = api.viewResources(
                Collections.emptyList(),
                Collections.singletonList(rscName),
                Collections.emptyList(),
                null,
                null,
                null);
        for (ResourceWithVolumes rsc : resources) {
            if (!rsc.getVolumes().isEmpty()) {
                // CloudStack resource always only have 1 volume
                String devicePath = rsc.getVolumes().get(0).getDevicePath();
                if (devicePath != null && !devicePath.isEmpty()) {
                    s_logger.debug(String.format("getDevicePath: %s -> %s", rscName, devicePath));
                    return devicePath;
                }
            }
        }

        final String errMsg = "viewResources didn't return resources or volumes for " + rscName;
        s_logger.error(errMsg);
        throw new CloudRuntimeException("Linstor: " + errMsg);
    }
}
