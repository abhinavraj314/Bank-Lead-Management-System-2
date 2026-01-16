package com.bankleads.bank_leads_backend.util;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

public class ResponseUtil {
    
    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message, HttpStatus status) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
        return ResponseEntity.status(status).body(response);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return success(data, null, HttpStatus.OK);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        return success(data, message, HttpStatus.OK);
    }
    
    public static <T> ResponseEntity<ApiResponse<List<T>>> successWithPagination(Page<T> page) {
        ApiResponse.PaginationInfo pagination = ApiResponse.PaginationInfo.builder()
                .page(page.getNumber() + 1)
                .limit(page.getSize())
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .build();
        
        ApiResponse<List<T>> response = ApiResponse.<List<T>>builder()
                .success(true)
                .data(page.getContent())
                .pagination(pagination)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status, Object details) {
        ApiResponse.ErrorInfo error = ApiResponse.ErrorInfo.builder()
                .message(message)
                .details(details)
                .build();
        
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
        
        return ResponseEntity.status(status).body(response);
    }
    
    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status) {
        return error(message, status, null);
    }
}
