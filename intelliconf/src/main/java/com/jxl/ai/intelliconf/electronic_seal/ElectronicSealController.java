package com.jxl.ai.intelliconf.electronic_seal;

import com.jxl.ai.intelliconf.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/intelli-conf/v1/electronic-seal")
@RequiredArgsConstructor
public class ElectronicSealController {

    private final ElectronicSealService electronicSealService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(
            @RequestParam("name") String name,
            @RequestParam(value = "centerText", defaultValue = "电子专用章") String centerText) {
        byte[] seal = electronicSealService.generateSeal(name, centerText);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("electronic-seal.png", StandardCharsets.UTF_8)
                        .build().toString())
                .body(seal);
    }

    @PostMapping(value = "/stamp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> stamp(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sealImage", required = false) MultipartFile sealImage,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "centerText", defaultValue = "电子专用章") String centerText,
            @RequestParam(value = "page", defaultValue = "last") String page,
            @RequestParam(value = "position", defaultValue = "bottom-right") String position,
            @RequestParam(value = "x", required = false) Float x,
            @RequestParam(value = "y", required = false) Float y,
            @RequestParam(value = "size", defaultValue = "120") Float size,
            @RequestParam(value = "opacity", defaultValue = "0.82") Float opacity) {
        validateUpload(file);
        if (file.getSize() > ElectronicSealService.MAX_PDF_BYTES) {
            throw new ClientException("PDF 文件不能超过 25 MB");
        }
        if (sealImage != null && !sealImage.isEmpty()
                && sealImage.getSize() > ElectronicSealService.MAX_SEAL_IMAGE_BYTES) {
            throw new ClientException("自定义印章图片不能超过 4 MB");
        }

        try {
            byte[] result = electronicSealService.stampPdf(
                    file.getBytes(),
                    sealImage == null || sealImage.isEmpty() ? null : sealImage.getBytes(),
                    new ElectronicSealOptions(name, centerText, page, position, x, y, size, opacity)
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(result.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(outputFileName(file.getOriginalFilename()), StandardCharsets.UTF_8)
                            .build().toString())
                    .body(result);
        } catch (IOException ex) {
            throw new ClientException("读取上传文件失败");
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("请选择 PDF 文件");
        }
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)
                || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ClientException("仅支持 PDF 文件");
        }
    }

    private String outputFileName(String originalName) {
        String safeName = StringUtils.hasText(originalName)
                ? originalName.replace('\\', '_').replace('/', '_').replaceAll("[\\r\\n\\\"]", "_")
                : "document.pdf";
        if (safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safeName = safeName.substring(0, safeName.length() - 4);
        }
        if (safeName.length() > 100) {
            safeName = safeName.substring(0, 100);
        }
        return safeName + "-盖章.pdf";
    }
}
