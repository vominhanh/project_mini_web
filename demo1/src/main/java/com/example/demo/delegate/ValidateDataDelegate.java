package com.example.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("validateData")
public class ValidateDataDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        Object retryCount = execution.getVariable("retryCount");
        if (retryCount == null) {
            execution.setVariable("retryCount", 0);
        }
    }
}
