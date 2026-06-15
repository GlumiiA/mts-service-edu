package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.aigul.mts_service.dto.ApplicationResponse;
import ru.aigul.mts_service.dto.BalanceResponse;
import ru.aigul.mts_service.dto.PaymentResponse;
import ru.aigul.mts_service.dto.TopUpRequest;
import ru.aigul.mts_service.mapper.AccountApplicationMapper;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.service.ApplicationService;
import ru.aigul.mts_service.service.BalanceService;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/account", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountController {

    private final ApplicationService applicationService;
    private final BalanceService balanceService;
    private final AccountApplicationMapper applicationMapper;

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('APPLICATION_READ_OWN')")
    public ResponseEntity<List<ApplicationResponse>> getMyApplications(Authentication auth) {
        List<Application> apps = applicationService.getApplicationsForUserEmail(auth.getName());
        List<ApplicationResponse> dto = apps.stream().map(applicationMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getMyBalance(Authentication auth) {
        BalanceResponse resp = balanceService.getBalanceResponseForUserEmail(auth.getName());
        return ResponseEntity.ok(resp);
    }

    @PostMapping(path = "/balance/top-up", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ACCOUNT_TOPUP')")
    public ResponseEntity<PaymentResponse> topUpBalance(Authentication auth, @Valid @RequestBody TopUpRequest req) {
        String paymentUrl = balanceService.createTopUpPayment(auth.getName(), req.getAmount())
                .orElseThrow(() -> new IllegalArgumentException("Amount must be greater than zero"));
        PaymentResponse resp = new PaymentResponse(paymentUrl);
        return ResponseEntity.ok(resp);
    }
}
