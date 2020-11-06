/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.spec.shared.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;

@Getter
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StatusSpec {

    private static final String SUB_RESOURCE_NAME_PLACEHOLDER = "unknown";

    private List<Condition> conditions = new ArrayList<>();
    private RolloutPhase phase = RolloutPhase.IDLE;
    private String subResourceName;
    private String message;

    public void idle() {
        idle(null);
    }

    public void idle(String message) {
        refreshDeployCondition(RolloutPhase.IDLE, false, message);
        refreshQueueCondition(RolloutPhase.IDLE, false, message);
    }

    public void installing() {
        installing(null);
    }

    public void installing(String message) {
        refreshDeployCondition(RolloutPhase.INSTALLING, false, message);
        refreshQueueCondition(RolloutPhase.INSTALLING, true, message);
    }

    public void upgrading() {
        upgrading(null);
    }

    public void upgrading(String message) {
        refreshDeployCondition(RolloutPhase.UPGRADING, false, message);
    }

    public void deleting() {
        deleting(null);
    }

    public void deleting(String message) {
        refreshDeployCondition(RolloutPhase.DELETING, false, message);
        refreshQueueCondition(RolloutPhase.DELETING, false, message);
    }

    public void succeeded() {
        succeeded(null, SUB_RESOURCE_NAME_PLACEHOLDER);
    }

    public void succeeded(String message, String subResourceName) {
        refreshDeployCondition(RolloutPhase.SUCCEEDED, subResourceName, true, message);
        refreshQueueCondition(RolloutPhase.SUCCEEDED, subResourceName, true, message);
    }

    public void failed() {
        failed(null);
    }

    public void failed(String message) {
        refreshDeployCondition(RolloutPhase.FAILED, false, message);
        refreshQueueCondition(RolloutPhase.FAILED, false, message);
    }


    public void refreshDeployCondition(RolloutPhase cStatus, boolean status, String message) {
        refreshDeployCondition(cStatus, SUB_RESOURCE_NAME_PLACEHOLDER, status, message);
    }

    public void refreshDeployCondition(RolloutPhase cStatus, String subResourceName, boolean status, String message) {
        Condition deployCondition;

        if (conditions.isEmpty()) {
            deployCondition = Condition.builder().type(Condition.Type.DEPLOYED.toString()).build();
            this.conditions.add(deployCondition);
        } else {
            deployCondition = conditions.stream()
                    .filter(condition -> condition.getType().equals(Condition.Type.DEPLOYED.toString()))
                    .findFirst().orElse(null);
            if (deployCondition == null) {
                deployCondition = Condition.builder().type(Condition.Type.DEPLOYED.toString()).build();
                this.conditions.add(deployCondition);
            }
        }

        String currentTime = Instant.now().truncatedTo(SECONDS).toString();

        deployCondition.setLastUpdateTime(currentTime);

        String currentStatus = Condition.Status.valueOf(status).toString();

        if (!currentStatus.equals(deployCondition.getStatus())) {
            deployCondition.setLastTransitionTime(currentTime);
        }

        this.message = message;
        this.phase = cStatus;
        this.subResourceName = subResourceName;

        deployCondition.setMessage(message);
        deployCondition.setReason(phase.getReason());
        deployCondition.setStatus(currentStatus);
    }

    public void refreshQueueCondition(RolloutPhase cStatus, boolean status, String message) {
        refreshQueueCondition(cStatus, SUB_RESOURCE_NAME_PLACEHOLDER, status, message);
    }

    public void refreshQueueCondition(RolloutPhase cStatus, String subResourceName, boolean status, String message) {
        Condition queueCondition;

        if (conditions.isEmpty()) {
            queueCondition = Condition.builder().type(Condition.Type.ENQUEUED.toString()).build();
            this.conditions.add(queueCondition);
        } else {
            queueCondition = conditions.stream()
                    .filter(condition -> condition.getType().equals(Condition.Type.ENQUEUED.toString()))
                    .findFirst().orElse(null);
            if (queueCondition == null) {
                queueCondition = Condition.builder().type(Condition.Type.ENQUEUED.toString()).build();
                this.conditions.add(queueCondition);
            }
        }

        String currentTime = Instant.now().truncatedTo(SECONDS).toString();

        queueCondition.setLastUpdateTime(currentTime);

        String currentStatus = Condition.Status.valueOf(status).toString();

        if (!currentStatus.equals(queueCondition.getStatus())) {
            queueCondition.setLastTransitionTime(currentTime);
        }

        this.message = message;
        this.phase = cStatus;
        this.subResourceName = subResourceName;

        queueCondition.setMessage(message);
        queueCondition.setReason(phase.getReason());
        queueCondition.setStatus(currentStatus);
    }
}
