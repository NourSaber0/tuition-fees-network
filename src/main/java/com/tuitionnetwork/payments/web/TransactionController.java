package com.tuitionnetwork.payments.web;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDto;
import com.tuitionnetwork.payments.dto.TransactionTabCountsDto;
import com.tuitionnetwork.payments.service.TransactionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    public TransactionController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransactionDto>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "institution", required = false) String institution,
            @RequestParam(value = "institutionType", required = false) String institutionType,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(transactionQueryService.listTransactions(
                status, search, institution, institutionType, method, dateFrom, dateTo, page, resolvedSize
        ));
    }

    @GetMapping("/tab-counts")
    public ResponseEntity<TransactionTabCountsDto> getTabCounts() {
        return ResponseEntity.ok(transactionQueryService.getTabCounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDetailDto> getDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(transactionQueryService.getTransactionDetail(id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "institution", required = false) String institution,
            @RequestParam(value = "institutionType", required = false) String institutionType,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        byte[] csv = transactionQueryService.exportTransactionsCsv(status, search, institution, institutionType, method, dateFrom, dateTo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "transactions-export.csv");

        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getStatusCode().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
