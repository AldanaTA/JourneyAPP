package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.item.StatusConditionDto;
import com.journey.api_rest_v1.service.StatusConditionService;

/**
 * REST endpoint for the status conditions under {@code /api/v1/status-conditions}.
 */
@RestController
@RequestMapping("/api/v1/status-conditions")
public class StatusConditionController {

    private final StatusConditionService statusConditionService;

    /** Wires the status condition service. */
    public StatusConditionController(StatusConditionService statusConditionService) {
        this.statusConditionService = statusConditionService;
    }

    /** Returns every status condition with its rules text. */
    @GetMapping()
    public ResponseEntity<List<StatusConditionDto>> getStatusConditions() {
        return new ResponseEntity<>(statusConditionService.getStatusConditions(), HttpStatus.OK);
    }
}
