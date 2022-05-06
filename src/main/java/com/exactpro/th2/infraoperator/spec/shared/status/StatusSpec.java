/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.spec.shared.status;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StatusSpec {

    private static final String SUB_RESOURCE_NAME_PLACEHOLDER = "unknown";

    private final List<Condition> conditions = new ArrayList<>();

    private RolloutPhase phase = RolloutPhase.IDLE;

    private String subResourceName;

    private String message;

    public void idle() {
        idle(null);
    }

    public void idle(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.IDLE, false, message);
    }

    public void installing() {
        installing(null);
    }

    public void installing(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.INSTALLING, false, message);
    }

    public void upgrading() {
        upgrading(null);
    }

    public void upgrading(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.UPGRADING, false, message);
    }

    public void deleting() {
        deleting(null);
    }

    public void deleting(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.DELETING, false, message);
    }

    public void succeeded() {
        succeeded(null, SUB_RESOURCE_NAME_PLACEHOLDER);
    }

    public void succeeded(String message, String subResourceName) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.SUCCEEDED, subResourceName, true, message);
    }

    public void failed(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.FAILED, false, message);
    }

    public void disabled(String message) {
        refreshCondition(Condition.Type.DEPLOYED, RolloutPhase.DISABLED, false, message);
    }

    private void refreshCondition(Condition.Type type, RolloutPhase phase, boolean status, String message) {
        refreshCondition(type, phase, SUB_RESOURCE_NAME_PLACEHOLDER, status, message);
    }

    private void refreshCondition(Condition.Type type, RolloutPhase phase, String subResourceName,
                                  boolean status, String message) {

        Condition condition;
        if (conditions.isEmpty()) {
            condition = new Condition(type.toString());
            this.conditions.add(condition);
        } else {
            condition = conditions.get(0);
        }

        String currentTime = Instant.now().truncatedTo(SECONDS).toString();
        condition.setLastUpdateTime(currentTime);

        String currentStatus = Condition.Status.valueOf(status).toString();
        if (!currentStatus.equals(condition.getStatus())) {
            condition.setLastTransitionTime(currentTime);
        }

        this.message = message;
        this.phase = phase;
        this.subResourceName = subResourceName;

        condition.setMessage(message);
        condition.setReason(phase.getReason());
        condition.setStatus(currentStatus);
    }

    public List<Condition> getConditions() {
        return this.conditions;
    }

    public RolloutPhase getPhase() {
        return this.phase;
    }

    public String getSubResourceName() {
        return this.subResourceName;
    }

    public String getMessage() {
        return this.message;
    }

    public String toString() {
        return "StatusSpec(conditions=" + this.getConditions() + ", phase=" + this.getPhase() +
                ", subResourceName=" + this.getSubResourceName() + ", message=" + this.getMessage() + ")";
    }
}
