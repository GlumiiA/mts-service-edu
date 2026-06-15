package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.model.BillingTransaction;
import ru.aigul.mts_service.repository.ApplicationRepository;
import ru.aigul.mts_service.repository.BillingTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/demo/state", produces = MediaType.APPLICATION_JSON_VALUE)
public class DemoStateController {

    private static final String TYPE_DEBIT = "DEBIT";

    private final ApplicationRepository applicationRepository;
    private final BillingTransactionRepository billingTransactionRepository;

    @GetMapping("/{applicationId}")
    public DemoStateResponse getState(@PathVariable Long applicationId) {
        Application application = applicationRepository.findByIdWithUserAndTariff(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        return toResponse(application);
    }

    @GetMapping("/all")
    public List<DemoStateResponse> getAllStates() {
        return applicationRepository.findAllWithUserAndTariffOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private DemoStateResponse toResponse(Application application) {
        List<BillingTransactionDto> transactions = billingTransactionRepository
                .findAllByApplicationIdOrderByCreatedAtAsc(application.getId())
                .stream()
                .map(this::toTransactionDto)
                .toList();

        BigDecimal debitTotal = transactions.stream()
                .filter(tx -> TYPE_DEBIT.equals(tx.type()))
                .map(BillingTransactionDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPrice = application.getLockedPrice() == null
                ? BigDecimal.ZERO
                : application.getLockedPrice();
        boolean debitRequired = application.getStatus() == ApplicationStatus.PROCESSING
                || application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.CONNECTED;
        boolean consistent = !debitRequired || debitTotal.compareTo(totalPrice) == 0;

        return new DemoStateResponse(
                toApplicationDto(application, totalPrice),
                new BillingStateDto(transactions),
                consistent
        );
    }

    private DemoApplicationDto toApplicationDto(Application application, BigDecimal totalPrice) {
        return new DemoApplicationDto(
                application.getId(),
                application.getUser().getId(),
                application.getUser().getEmail(),
                application.getTariff().getId(),
                application.getTariff().getName(),
                application.getAddress(),
                application.getStatus(),
                totalPrice,
                application.getTaigaTaskId(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private BillingTransactionDto toTransactionDto(BillingTransaction transaction) {
        return new BillingTransactionDto(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getApplicationId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

    public record DemoStateResponse(
            DemoApplicationDto application,
            BillingStateDto billing,
            boolean consistent
    ) {
    }

    public record DemoApplicationDto(
            Long id,
            Long userId,
            String userEmail,
            Long tariffId,
            String tariffName,
            String address,
            ApplicationStatus status,
            BigDecimal totalPrice,
            Long taigaTaskId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record BillingStateDto(
            List<BillingTransactionDto> transactions
    ) {
    }

    public record BillingTransactionDto(
            Long id,
            Long userId,
            Long applicationId,
            BigDecimal amount,
            String type,
            String description,
            OffsetDateTime createdAt
    ) {
    }
}
