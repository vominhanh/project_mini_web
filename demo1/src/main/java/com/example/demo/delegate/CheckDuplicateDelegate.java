package com.example.demo.delegate;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("checkDuplicate")
public class CheckDuplicateDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        String roomName = (String) execution.getVariable("roomName");
        String city = (String) execution.getVariable("city");
        if (roomName == null || roomName.isBlank()) {
            throw new BpmnError("VALIDATION_ERROR", "Ten phong khong duoc de trong");
        }
        if (city != null && city.length() > 120) {
            throw new BpmnError("VALIDATION_ERROR", "Thong tin thanh pho khong hop le");
        }
    }
}
