package msa.bookloan.adapter.in.web.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.web.controller.dto.request.LoanCreateRequest;
import msa.bookloan.adapter.in.web.controller.dto.response.LoanCreateResponse;
import msa.bookloan.application.service.LoanService;
import msa.bookloan.application.service.dto.LoanCreateResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/loans")
public class LoanController {

    private final LoanService loanService;

    @Operation(summary = "도서 대출 생성", description = "사용자가 특정 도서를 대출합니다.")
    @ApiResponse(responseCode = "202", description = "요청 접수됨(비동기 처리)")
    @PostMapping
    public ResponseEntity<LoanCreateResponse> create(@Valid @RequestBody LoanCreateRequest request) {
        // TODO : 시큐리티 붙이면 바꿀 로직
        Long memberId = 1L;
        LoanCreateResult result = loanService.createLoan(memberId, request);

        String statusUrl = ServletUriComponentsBuilder
                .fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(result.loanId())
                .toUriString();
        URI location = URI.create(statusUrl);

        LoanCreateResponse body = new LoanCreateResponse(
                result.loanId(),
                result.sagaId(),
                result.status(),
                statusUrl);

        return ResponseEntity.accepted()
                .location(location)
                .body(body);
    }


}
