package ru.aigul.mts_service.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.aigul.mts_service.dto.CursorPage;
import ru.aigul.mts_service.dto.application.*;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.service.ApplicationApprovalWorkflowService;
import ru.aigul.mts_service.service.ApplicationService;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationApprovalWorkflowService applicationApprovalWorkflowService;

    @Value("${app.pagination.default-limit}")
    private int defaultLimit;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPLICATION_CREATE')")
    public ApplicationDto create(Authentication auth,
                                 @Valid @RequestBody ApplicationCreateDto dto) {
        return applicationService.create(auth.getName(), dto);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPLICATION_READ_OWN') or hasAuthority('APPLICATION_READ_ALL')")
    public CursorPage<ApplicationDto> list(
            Authentication auth,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return applicationService.list(auth, status, after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('APPLICATION_READ_OWN') or hasAuthority('APPLICATION_READ_ALL')")
    public ApplicationDetailDto getDetail(@PathVariable Long applicationId) {
        return applicationService.getDetail(applicationId);
    }

    @PostMapping("/{applicationId}/approve")
    @PreAuthorize("hasAuthority('APPLICATION_APPROVE')")
    public ResponseEntity<AsyncApprovalAcceptedDto> approve(Authentication auth, @PathVariable Long applicationId) {
        String correlationId = java.util.UUID.randomUUID().toString();
        applicationApprovalWorkflowService.approveAsynchronously(applicationId, auth.getName(), correlationId);
        AsyncApprovalAcceptedDto body = new AsyncApprovalAcceptedDto(applicationId, correlationId, "ACCEPTED");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/{applicationId}/reject")
    @PreAuthorize("hasAuthority('APPLICATION_REJECT')")
    public ResponseEntity<ApplicationDto> reject(@PathVariable Long applicationId,
                                                  @Valid @RequestBody ApplicationRejectDto dto) {
        return ResponseEntity.ok(applicationService.reject(applicationId, dto));
    }
}
