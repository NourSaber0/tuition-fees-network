package com.tuitionnetwork.fees.web;

import com.tuitionnetwork.fees.dto.FeeCategoryDto;
import com.tuitionnetwork.fees.service.SchoolFeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/fee-categories", "/fee-categories"})
public class FeeCategoryController {

    private final SchoolFeeService schoolFeeService;

    @Autowired
    public FeeCategoryController(SchoolFeeService schoolFeeService) {
        this.schoolFeeService = schoolFeeService;
    }

    @GetMapping
    public ResponseEntity<List<FeeCategoryDto>> getFeeCategories() {
        return ResponseEntity.ok(schoolFeeService.getFeeCategories());
    }
}
