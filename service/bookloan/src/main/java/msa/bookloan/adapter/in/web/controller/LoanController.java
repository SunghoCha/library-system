package msa.bookloan.adapter.in.web.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import msa.bookloan.adapter.in.web.controller.dto.LoanCreateRequest;
import msa.bookloan.application.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/loans")
public class LoanController {

    private final LoanService loanService;

    @Operation(summary = "도서 대출 생성", description = "사용자가 특정 도서를 대출합니다.") // Swagger Operation
    @ApiResponse(responseCode = "201", description = "대출 성공")
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody LoanCreateRequest request) {
        loanService.createLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
