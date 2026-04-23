package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.aigul.mts_service.dto.demo.DemoStateDto;
import ru.aigul.mts_service.service.DemoService;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @GetMapping("/state/{applicationId}")
    public DemoStateDto getState(@PathVariable Long applicationId) {
        return demoService.getState(applicationId);
    }

    @GetMapping("/state/all")
    public List<DemoStateDto> getAllStates() {
        return demoService.getAllStates();
    }
}