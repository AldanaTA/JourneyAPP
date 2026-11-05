package com.journey.api_rest_v1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;

import com.journey.api_rest_v1.service.PowerService;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/api/v1/powers") 
public class PowerController {
    
    private final PowerService powerService;

    public PowerController(PowerService powerService) {
        this.powerService = powerService;
    }
    @PostMapping()
    public ResponseEntity<PowerResponseDto> createPower(@Valid @RequestBody CreatePowerRequest request) {
       
        return new ResponseEntity<>(powerService.createPower(request), HttpStatus.CREATED);
    }
    
}
