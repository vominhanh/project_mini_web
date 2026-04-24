package com.example.demo.service.work_flow;

import com.example.demo.dto.request.RoomSubmissionRequest;
import com.example.demo.dto.notification.WorkflowNotificationEvent;
import com.example.demo.dto.response.RoomResponseDto;
import com.example.demo.dto.response.RoomTaskDto;
import com.example.demo.dto.response.UserSummaryDto;
import com.example.demo.entity.Room;
import com.example.demo.entity.User;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.UserRepository;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomWorkflowService {
    private static final String PROCESS_KEY = "create_room_process";

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final WorkflowEventPublisher workflowEventPublisher;
    private final String adminUserId;

    public RoomWorkflowService(RoomRepository roomRepository, UserRepository userRepository,
            RuntimeService runtimeService, TaskService taskService,
            WorkflowEventPublisher workflowEventPublisher,
            @Value("${camunda.bpm.admin-user.id:demo}") String adminUserId) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.workflowEventPublisher = workflowEventPublisher;
        this.adminUserId = adminUserId;
    }

    @CacheEvict(cacheNames = { "rooms_admin_list", "rooms_user_list" }, allEntries = true)
    public String submitRoom(RoomSubmissionRequest request, Jwt jwt) {
        String ownerEmail = extractEmail(jwt);
        User owner = upsertOwner(jwt, ownerEmail, request.getPhoneNumber());
        Room room = new Room();
        applyRoomData(room, request);
        room.setOwner(owner);
        room.setStatus("PENDING");
        room.setIsAvailable(false);
        room.setCreatedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());
        room = roomRepository.save(room);

        Map<String, Object> variables = new HashMap<>();
        variables.put("roomId", room.getId());
        variables.put("roomName", room.getName());
        variables.put("ownerEmail", owner.getEmail());
        variables.put("ownerDisplayName", buildDisplayName(owner));
        variables.put("roomOwner", owner.getEmail());
        variables.put("price", room.getPrice());
        variables.put("city", room.getCity());
        variables.put("retryCount", 0);
        runtimeService.startProcessInstanceByKey(PROCESS_KEY, variables);
        return "Da gui yeu cau tao phong. Vui long cho admin duyet.";
    }

    @Cacheable(cacheNames = "rooms_user_list", key = "#ownerEmail")
    public List<RoomResponseDto> findMyRooms(String ownerEmail) {
        return roomRepository.findTop10ByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @Cacheable(cacheNames = "rooms_admin_list", key = "'all'")
    public List<RoomResponseDto> findAllRooms() {
        return roomRepository.findTop10ByOrderByCreatedAtDesc().stream().map(this::toRoomResponse).toList();
    }

    public List<RoomTaskDto> getAdminApprovalTasks() {
        return taskService.createTaskQuery()
                .processDefinitionKey(PROCESS_KEY)
                .taskDefinitionKey("adminApproval")
                .or()
                .taskCandidateGroup("admin")
                .taskAssignee(adminUserId)
                .endOr()
                .active()
                .list()
                .stream()
                .map(this::toTaskDto)
                .toList();
    }

    public List<RoomTaskDto> getOwnerUpdateTasks(String ownerEmail) {
        return taskService.createTaskQuery()
                .processDefinitionKey(PROCESS_KEY)
                .taskAssignee(ownerEmail)
                .taskDefinitionKey("updateRoom")
                .active()
                .list()
                .stream()
                .map(this::toTaskDto)
                .toList();
    }

    @CacheEvict(cacheNames = { "rooms_admin_list", "rooms_user_list" }, allEntries = true)
    public void decideByAdmin(String taskId, boolean approved) {
        Task task = requireTask(taskId);
        Long roomId = readLongVariable(task.getProcessInstanceId(), "roomId");
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay room"));
        room.setStatus(approved ? "APPROVED" : "REJECTED");
        room.setIsAvailable(approved);
        room.setUpdatedAt(LocalDateTime.now());
        roomRepository.save(room);

        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);
        taskService.complete(task.getId(), variables);
    }

    @CacheEvict(cacheNames = { "rooms_admin_list", "rooms_user_list" }, allEntries = true)
    public void resubmitByOwner(String taskId, String ownerEmail, RoomSubmissionRequest request) {
        Task task = requireTask(taskId);
        if (!ownerEmail.equals(task.getAssignee())) {
            throw new IllegalArgumentException("Task khong thuoc nguoi dung hien tai");
        }

        Long roomId = readLongVariable(task.getProcessInstanceId(), "roomId");
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay room"));
        User owner = room.getOwner();
        if (owner != null) {
            owner.setPhoneNumber(normalizePhone(request.getPhoneNumber()));
            owner.setUpdatedAt(LocalDateTime.now());
            userRepository.save(owner);
        }
        applyRoomData(room, request);
        room.setStatus("PENDING");
        room.setIsAvailable(false);
        room.setUpdatedAt(LocalDateTime.now());
        roomRepository.save(room);

        Integer retryCount = (Integer) runtimeService.getVariable(task.getProcessInstanceId(), "retryCount");
        int nextRetryCount = retryCount == null ? 1 : retryCount + 1;

        WorkflowNotificationEvent updateEvent = new WorkflowNotificationEvent();
        updateEvent.setEventType("USER_TO_ADMIN_UPDATE");
        updateEvent.setRoomId(room.getId());
        updateEvent.setRoomName(room.getName());
        updateEvent.setOwnerEmail(ownerEmail);
        updateEvent.setRetryCount(nextRetryCount);
        updateEvent.setStatus("PENDING_REVIEW_AFTER_UPDATE");
        updateEvent.setMessage("Nguoi dung da cap nhat phong, can admin duyet lai (lan " + nextRetryCount + ")");
        updateEvent.setCreatedAt(LocalDateTime.now());
        workflowEventPublisher.publishNotification(updateEvent);

        Map<String, Object> variables = new HashMap<>();
        variables.put("roomName", room.getName());
        variables.put("price", room.getPrice());
        variables.put("city", room.getCity());
        variables.put("retryCount", nextRetryCount);
        taskService.complete(task.getId(), variables);
    }

    private Task requireTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("Khong tim thay task");
        }
        return task;
    }

    private Long readLongVariable(String processInstanceId, String variableName) {
        Object variable = runtimeService.getVariable(processInstanceId, variableName);
        if (variable instanceof Long value) {
            return value;
        }
        if (variable instanceof Integer value) {
            return value.longValue();
        }
        throw new IllegalArgumentException("Khong doc duoc bien " + variableName);
    }

    private RoomTaskDto toTaskDto(Task task) {
        String processInstanceId = task.getProcessInstanceId();
        RoomTaskDto dto = new RoomTaskDto();
        dto.setTaskId(task.getId());
        dto.setTaskName(task.getName());
        dto.setTaskDefinitionKey(task.getTaskDefinitionKey());
        Long roomId = readLongVariable(processInstanceId, "roomId");
        dto.setRoomId(roomId);
        dto.setRoomName((String) runtimeService.getVariable(processInstanceId, "roomName"));
        dto.setStatus(resolveRoomStatus(roomId));
        roomRepository.findById(roomId).ifPresent(room -> dto.setUser(toUserSummary(room.getOwner())));
        Object retryCount = runtimeService.getVariable(processInstanceId, "retryCount");
        dto.setRetryCount(retryCount instanceof Integer i ? i : 0);
        return dto;
    }

    private String resolveRoomStatus(Long roomId) {
        return roomRepository.findById(roomId).map(Room::getStatus).orElse("UNKNOWN");
    }

    private RoomResponseDto toRoomResponse(Room room) {
        RoomResponseDto dto = new RoomResponseDto();
        dto.setId(room.getId());
        dto.setName(room.getName());
        dto.setDescription(room.getDescription());
        dto.setPrice(room.getPrice());
        dto.setCapacity(room.getCapacity());
        dto.setBedType(room.getBedType());
        dto.setThumbnail(room.getThumbnail());
        dto.setStatus(room.getStatus());
        dto.setIsAvailable(room.getIsAvailable());
        dto.setAddress(room.getAddress());
        dto.setCity(room.getCity());
        dto.setDistrict(room.getDistrict());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setUpdatedAt(room.getUpdatedAt());
        return dto;
    }

    private UserSummaryDto toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        UserSummaryDto dto = new UserSummaryDto();
        dto.setId(user.getId());
        dto.setFirstname(user.getFirstName());
        dto.setLastname(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setPhoneNumber(user.getPhoneNumber());
        return dto;
    }

    private User upsertOwner(Jwt jwt, String email, String phoneNumber) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFirstName(resolveFirstName(jwt));
        user.setLastName(resolveLastName(jwt));
        user.setRole(resolveRole(jwt));
        user.setPhoneNumber(normalizePhone(phoneNumber));
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword("OAUTH2_LOGIN");
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private void applyRoomData(Room room, RoomSubmissionRequest request) {
        room.setName(request.getName());
        room.setDescription(request.getDescription());
        room.setPrice(request.getPrice());
        room.setCapacity(request.getCapacity());
        room.setBedType(request.getBedType());
        room.setThumbnail(request.getThumbnail());
        room.setAddress(request.getAddress());
        room.setCity(request.getCity());
        room.setDistrict(request.getDistrict());
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String normalized = phoneNumber.trim();
        return normalized.isBlank() ? null : normalized;
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

    private String resolveDisplayName(Jwt jwt, String fallback) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return fallback;
    }

    private String resolveFirstName(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        if (given != null && !given.isBlank()) {
            return given.trim();
        }
        String display = resolveDisplayName(jwt, "");
        if (display.isBlank()) {
            return "User";
        }
        String[] parts = display.trim().split("\\s+", 2);
        return parts[0];
    }

    private String resolveLastName(Jwt jwt) {
        String family = jwt.getClaimAsString("family_name");
        if (family != null && !family.isBlank()) {
            return family.trim();
        }
        String display = resolveDisplayName(jwt, "");
        if (display.isBlank()) {
            return "";
        }
        String[] parts = display.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private String buildDisplayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? user.getEmail() : full;
    }

    private String resolveRole(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> roleList) {
                for (Object role : roleList) {
                    if (role != null && "admin".equalsIgnoreCase(role.toString())) {
                        return "ADMIN";
                    }
                }
            }
        }
        return "USER";
    }
}
