package com.journey.api_rest_v1.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerFilterRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.service.PowerService;

import org.springframework.http.HttpStatus;

import com.journey.api_rest_v1.service.campaign.ContentAccess;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequestMapping("/api/v1/powers") 
public class PowerController {
    
    private final PowerService powerService;
    private final ContentAccess contentAccess;

    public PowerController(PowerService powerService, ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
        this.powerService = powerService;
    }
    @PostMapping()
    public ResponseEntity<PowerResponseDto> createPower(@Valid @RequestBody CreatePowerRequest request) {
       
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(powerService.createPower(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PowerResponseDto> getPower(@PathVariable String id) {
        contentAccess.requireReadable(ContentAccess.Kind.POWER, id);
        return new ResponseEntity<>(powerService.getPowerById(id), HttpStatus.OK);
    }

    @GetMapping()
    public ResponseEntity<Iterable<PowerResponseDto>> getAllPowers(
        @RequestParam(defaultValue = "0") int page, 
        @RequestParam(defaultValue = "5") int size, 
        @RequestParam(defaultValue = "name") String sortBy, 
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(powerService.getAllPowers(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<PowerResponseDto>> getAllPowersByCategory(@Valid @RequestBody PowerFilterRequest request) {
        return new ResponseEntity<>(
            powerService.filterPowers(request),
            HttpStatus.OK
        );
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<PowerResponseDto> putMethodName(@PathVariable String id, @Valid @RequestBody CreatePowerRequest request) {
        
        contentAccess.requireWritable(ContentAccess.Kind.POWER, id);
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(powerService.updatePower(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePower(@PathVariable String id) {
        contentAccess.requireWritable(ContentAccess.Kind.POWER, id);
        String result = powerService.deletePower(id);
        return ResponseEntity.ok(result);
    }
}
