package org.jumpmind.metl.core.runtime;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.metl.core.model.Agent;
import org.jumpmind.metl.core.model.AgentDeployment;
import org.jumpmind.metl.core.model.Execution;
import org.jumpmind.metl.core.model.ExecutionStatus;
import org.jumpmind.metl.core.model.ExecutionStep;
import org.jumpmind.metl.core.model.ExecutionStepLog;
import org.jumpmind.metl.core.runtime.component.ComponentContext;
import org.jumpmind.metl.core.runtime.component.ComponentStatistics;
import org.jumpmind.metl.core.runtime.flow.AsyncRecorder;
import org.jumpmind.util.AppUtils;

public class ExecutionTrackerRecorder extends ExecutionTrackerLogger {

    final long TIME_BETWEEN_MESSAGE_UPDATES_IN_MS = 5000;

    AsyncRecorder recorder;

    Agent agent;

    Map<String, ExecutionStep> steps;

    Date startTime;

    public ExecutionTrackerRecorder(Agent agent, AgentDeployment agentDeployment, AsyncRecorder recorder) {
        super(agentDeployment);
        this.recorder = recorder;
        this.agent = agent;
    }

    @Override
    public void beforeFlow(String executionId) {
        super.beforeFlow(executionId);
        this.steps = new HashMap<String, ExecutionStep>();
        this.startTime = new Date();
        this.recorder.record(getExecution());
    }

    private Execution getExecution() {
        Execution execution = new Execution();
        execution.setId(executionId);
        execution.setStartTime(startTime);
        execution.setStatus(ExecutionStatus.RUNNING.name());
        execution.setAgentId(deployment.getAgentId());
        execution.setFlowId(deployment.getFlowId());
        execution.setAgentName(agent.getName());
        execution.setHostName(AppUtils.getHostName());
        execution.setFlowName(deployment.getFlow().getName());
        execution.setDeploymentName(deployment.getName());
        execution.setDeploymentId(deployment.getId());
        execution.setLastUpdateTime(new Date());
        return execution;
    }

    @Override
    public void afterFlow() {
        super.afterFlow();
        Execution execution = getExecution();
        execution.setEndTime(new Date());
        ExecutionStatus status = ExecutionStatus.DONE;
        for (ExecutionStep executionStep : steps.values()) {
            if (ExecutionStatus.ERROR.name().equals(executionStep.getStatus())) {
                status = ExecutionStatus.ERROR;
            }

            if (ExecutionStatus.CANCELLED.name().equals(executionStep.getStatus())) {
                status = ExecutionStatus.CANCELLED;
            }
        }
        execution.setStatus(status.name());
        this.recorder.record(execution);
    }

    @Override
    public void flowStepStarted(ComponentContext context) {
        super.flowStepStarted(context);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        if (step == null) {
            step = new ExecutionStep();
            this.steps.put(context.getFlowStep().getId(), step);
        }
        step.setExecutionId(executionId);
        step.setApproximateOrder(context.getFlowStep().getApproximateOrder());
        step.setComponentName(context.getFlowStep().getComponent().getName());
        step.setFlowStepId(context.getFlowStep().getId());
        step.setStatus(ExecutionStatus.READY.name());
        this.recorder.record(step);
    }
    
    @Override
    public void beforeHandle(ComponentContext context) {
        super.beforeHandle(context);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        Date lastUpdateTime = step.getLastUpdateTime();
        if (lastUpdateTime == null || (System.currentTimeMillis() - lastUpdateTime.getTime() > TIME_BETWEEN_MESSAGE_UPDATES_IN_MS)) {
            if (step.getStartTime() == null) {
                step.setStartTime(new Date());
            }
            if (!step.getStatus().equals(ExecutionStatus.ERROR.name())) {
                step.setStatus(ExecutionStatus.RUNNING.name());
            }
            step.setLastUpdateTime(new Date());
            this.recorder.record(step);
        }
    }
    
    @Override
    public void updateStatistics(ComponentContext context) {
        super.updateStatistics(context);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        Date lastUpdateTime = step.getLastUpdateTime();
        if (lastUpdateTime == null || (System.currentTimeMillis() - lastUpdateTime.getTime() > TIME_BETWEEN_MESSAGE_UPDATES_IN_MS)) {
            ComponentStatistics stats = context.getComponentStatistics();
            if (stats != null) {
                step.setEntitiesProcessed(stats.getNumberEntitiesProcessed());
                step.setMessagesReceived(stats.getNumberInboundMessages());
                step.setMessagesProduced(stats.getNumberOutboundMessages());
            }
            step.setLastUpdateTime(new Date());
            this.recorder.record(step);
        }
    }

    @Override
    public void afterHandle(ComponentContext context, Throwable error) {
        super.afterHandle(context, error);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        Date lastUpdateTime = step.getLastUpdateTime();
        if (lastUpdateTime == null || (System.currentTimeMillis() - lastUpdateTime.getTime() > TIME_BETWEEN_MESSAGE_UPDATES_IN_MS)) {
            step.setStatus(error != null ? ExecutionStatus.ERROR.name() : ExecutionStatus.READY.name());
            ComponentStatistics stats = context.getComponentStatistics();
            if (stats != null) {
                step.setEntitiesProcessed(stats.getNumberEntitiesProcessed());
                step.setMessagesReceived(stats.getNumberInboundMessages());
                step.setMessagesProduced(stats.getNumberOutboundMessages());
            }
            step.setLastUpdateTime(new Date());
            this.recorder.record(step);
        }
    }

    @Override
    public void flowStepFinished(ComponentContext context, Throwable error, boolean cancelled) {
        super.flowStepFinished(context, error, cancelled);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        if (step != null) {
            if (step.getStartTime() == null) {
                step.setStartTime(new Date());
            }
            step.setEndTime(new Date());
            ExecutionStatus status = ExecutionStatus.DONE;
            if (cancelled) {
                status = ExecutionStatus.CANCELLED;
            }
            if (error != null) {
                status = ExecutionStatus.ERROR;
            }
            step.setStatus(status.name());
            ComponentStatistics stats = context.getComponentStatistics();
            if (stats != null) {
                step.setEntitiesProcessed(stats.getNumberEntitiesProcessed());
                step.setMessagesReceived(stats.getNumberInboundMessages());
                step.setMessagesProduced(stats.getNumberOutboundMessages());
            }
            step.setLastUpdateTime(new Date());
            this.recorder.record(step);
        }
    }

    @Override
    public void flowStepFailedOnComplete(ComponentContext context, Throwable error) {
        super.flowStepFailedOnComplete(context, error);
        ExecutionStep step = steps.get(context.getFlowStep().getId());
        if (step != null) {
            step.setStatus(ExecutionStatus.ERROR.name());
            step.setLastUpdateTime(new Date());
            this.recorder.record(step);
        }
    }

    @Override
    public void log(LogLevel level, ComponentContext context, String output, Object... args) {
        super.log(level, context, output, args);
        if (deployment.asLogLevel().log(level)) {
            ExecutionStepLog log = new ExecutionStepLog();
            log.setExecutionStepId(steps.get(context.getFlowStep().getId()).getId());
            log.setLevel(level.name());
            if (args != null && args.length > 0) {
                output = String.format(output, args);
            }
            log.setLogText(output);
            this.recorder.record(log);
        }
    }

}