package org.xbib.netty.http.client.test.pool;

import io.netty.channel.epoll.Epoll;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DisableTestCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (Epoll.isAvailable()) {
            return ConditionEvaluationResult.enabled("Test enabled");
        } else {
            return ConditionEvaluationResult.disabled("Test disabled");
        }
    }
}
