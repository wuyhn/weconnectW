package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private static final String UPLOAD_DIR = "uploads";

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1001).message("File rỗng").build());
        }

        // Validate image type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1002).message("Chỉ chấp nhận file ảnh").build());
        }

        try {
            // Create uploads directory if not exists
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            } else {
                // Fallback extension from content type
                if ("image/png".equals(contentType)) extension = ".png";
                else if ("image/gif".equals(contentType)) extension = ".gif";
                else if ("image/webp".equals(contentType)) extension = ".webp";
                else extension = ".jpg";
            }
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return accessible URL
            String imageUrl = "/uploads/" + filename;
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000).message("Upload thành công")
                    .result(imageUrl).build());

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(ApiResponse.builder()
                    .code(1003).message("Lỗi lưu file: " + e.getMessage()).build());
        }
    }
}
