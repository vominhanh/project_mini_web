package com.example.demo.controller.room;

import com.example.demo.dto.request.RoomDecisionRequest;
import com.example.demo.dto.request.RoomSubmissionRequest;
import com.example.demo.dto.response.RoomResponseDto;
import com.example.demo.dto.response.RoomTaskDto;
import com.example.demo.service.work_flow.RoomWorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "http://localhost:4200")
public class RoomWorkflowController {
    private final RoomWorkflowService roomWorkflowService;

    public RoomWorkflowController(RoomWorkflowService roomWorkflowService) {
        this.roomWorkflowService = roomWorkflowService;
    }

    @PostMapping("/submit")
    public Map<String, String> submitRoom(@Valid @RequestBody RoomSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return Map.of("message", roomWorkflowService.submitRoom(request, jwt));
    }

    @GetMapping("/my")
    public List<RoomResponseDto> myRooms(@AuthenticationPrincipal Jwt jwt) {
        return roomWorkflowService.findMyRooms(extractEmail(jwt));
    }

    @GetMapping("/my/update-tasks")
    public List<RoomTaskDto> myUpdateTasks(@AuthenticationPrincipal Jwt jwt) {
        return roomWorkflowService.getOwnerUpdateTasks(extractEmail(jwt));
    }

    @PostMapping("/my/update-tasks/{taskId}/resubmit")
    public Map<String, String> resubmitRoom(@PathVariable String taskId,
            @Valid @RequestBody RoomSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        roomWorkflowService.resubmitByOwner(taskId, extractEmail(jwt), request);
        return Map.of("message", "Da cap nhat phong va gui duyet lai.");
    }

    @GetMapping("/admin/tasks")
    public List<RoomTaskDto> adminTasks() {
        return roomWorkflowService.getAdminApprovalTasks();
    }

    @PostMapping("/admin/tasks/{taskId}/decision")
    public Map<String, String> decideTask(@PathVariable String taskId, @RequestBody RoomDecisionRequest request) {
        roomWorkflowService.decideByAdmin(taskId, request.isApproved());
        return Map.of("message", "Da xu ly task admin.");
    }

    @GetMapping("/admin/rooms")
    public List<RoomResponseDto> adminRooms() {
        return roomWorkflowService.findAllRooms();
    }

    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase();
        }
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername.trim().toLowerCase() + "@local";
        }
        return jwt.getSubject() + "@local";
    }
}
