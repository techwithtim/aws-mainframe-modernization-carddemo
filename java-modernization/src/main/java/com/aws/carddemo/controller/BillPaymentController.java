package com.aws.carddemo.controller;

import com.aws.carddemo.dto.BillPaymentRequest;
import com.aws.carddemo.dto.BillPaymentResponse;
import com.aws.carddemo.service.BillPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billpayment")
public class BillPaymentController {

    @Autowired
    private BillPaymentService billPaymentService;

    @PostMapping
    public ResponseEntity<BillPaymentResponse> processBillPayment(@RequestBody BillPaymentRequest request) {
        BillPaymentResponse response = billPaymentService.processBillPayment(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
