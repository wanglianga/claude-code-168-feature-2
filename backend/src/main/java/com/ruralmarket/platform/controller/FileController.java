package com.ruralmarket.platform.controller;

import com.ruralmarket.platform.config.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${app.upload-dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return save(file);
    }

    /** 消费者投诉（免登录）上传支付凭证等图片，同样限制为图片类型 */
    @PostMapping("/public-upload")
    public Map<String, String> publicUpload(@RequestParam("file") MultipartFile file) throws IOException {
        return save(file);
    }

    private Map<String, String> save(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BizException("文件为空");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) throw new BizException("仅支持图片文件(jpg/png/gif/webp)");
        Path dir = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(dir.resolve(filename));
        return Map.of("url", "/files/" + filename);
    }
}
