package com.example.demo.delegate;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("checkBusinessRule")
public class CheckBusinessRuleDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        Object priceValue = execution.getVariable("price");
        if (!(priceValue instanceof BigDecimal price)) {
            throw new BpmnError("BUSINESS_RULE_ERROR", "Gia phong khong hop le");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BpmnError("BUSINESS_RULE_ERROR", "Gia phong phai lon hon 0");
        }
    }
}
