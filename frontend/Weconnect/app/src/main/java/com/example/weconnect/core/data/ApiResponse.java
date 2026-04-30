package com.example.weconnect.core.data;

/**
 * Response wrapper chung từ backend.
 * Backend trả về format: { "code": 1000, "message": "...", "result": ... }
 */
public class ApiResponse<T> {
    private int code;
    private String message;
    private T result;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getResult() { return result; }
    public void setResult(T result) { this.result = result; }

    public boolean isSuccess() { return code == 1000; }
}
