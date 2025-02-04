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
package org.apache.cloudstack.agent.api;

import java.util.List;

public class DeleteNetrisACLCommand extends NetrisCommand {
    Long vpcId;
    String vpcName;
    List<String> aclRuleNames;
    public DeleteNetrisACLCommand(long zoneId, Long accountId, Long domainId, String name, Long id, boolean isVpc, Long vpcId, String vpcName) {
        super(zoneId, accountId, domainId, name, id, isVpc);
        this.vpcId = vpcId;
        this.vpcName = vpcName;
    }

    public Long getVpcId() {
        return vpcId;
    }

    public String getVpcName() {
        return vpcName;
    }

    public List<String> getAclRuleNames() {
        return aclRuleNames;
    }

    public void setAclRuleNames(List<String> aclRuleNames) {
        this.aclRuleNames = aclRuleNames;
    }
}
