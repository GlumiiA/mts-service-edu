package ru.aigul.mts_service.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.aigul.mts_service.dto.ServiceCreateRequest;
import ru.aigul.mts_service.service.AdminService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SERVICE_MANAGE')")
public class AdminServiceController {

    private final AdminService adminService;

    @PostMapping("/services")
    public ResponseEntity<?> createService(@Valid @RequestBody ServiceCreateRequest req) {
        long id = adminService.createService(req.getName(), req.getPrice(), req.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("id", id));
    }
}
