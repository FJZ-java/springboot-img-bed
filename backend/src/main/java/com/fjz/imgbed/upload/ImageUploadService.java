package com.fjz.imgbed.upload;

import com.fjz.imgbed.common.BizException;
import com.fjz.imgbed.github.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 纯粹的「图片存入 GitHub」能力，不涉及任何业务记录。
 * 登录用户上传与游客上传都复用它，避免逻辑分叉。
 *
 * <p>安全校验三层：扩展名白名单 → 文件头魔数校验（防伪装文件）→ SVG 脚本检测（防存储型 XSS）。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif");

    /** SVG 里的危险特征：脚本标签、事件属性、伪协议等 */
    private static final Pattern SVG_DANGER = Pattern.compile(
            "<script|javascript:|data:text/html|<foreignobject|<iframe|<embed|<object|on[a-z]+\\s*=",
            Pattern.CASE_INSENSITIVE);

    private final GitHubService gitHubService;

    /** 校验并上传，返回仓库路径 + CDN 地址等元信息 */
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String origin = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String ext = "";
        int dot = origin.lastIndexOf('.');
        if (dot >= 0) {
            ext = origin.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持图片格式: " + String.join("/", ALLOWED_EXT));
        }

        // 存储路径：images/yyyy/MM/uuid.ext
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relativePath = datePath + "/" + fileName;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
        validateContent(ext, bytes);
        GitHubService.UploadResult result = gitHubService.upload(relativePath, bytes);

        return new StoredImage(fileName, origin, result.path(), result.sha(),
                result.cdnUrl(), file.getSize(), file.getContentType(),
                result.repoId(), result.repoName());
    }

    /** 第二层：文件内容真实性校验 */
    private void validateContent(String ext, byte[] bytes) {
        if (bytes.length == 0) {
            throw new BizException("文件不能为空");
        }
        if ("svg".equals(ext)) {
            validateSvg(bytes);
            return;
        }
        if (!matchesMagic(ext, bytes)) {
            throw new BizException("文件内容与扩展名 ." + ext + " 不符，已拒绝上传");
        }
    }

    /** 魔数（文件头）比对：扩展名可以随便改，文件头骗不了人 */
    private boolean matchesMagic(String ext, byte[] b) {
        return switch (ext) {
            case "png" -> startsWith(b, 0x89, 0x50, 0x4E, 0x47);
            case "jpg", "jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(b, 0x47, 0x49, 0x46, 0x38);
            case "bmp" -> startsWith(b, 0x42, 0x4D);
            case "ico" -> startsWith(b, 0x00, 0x00, 0x01, 0x00);
            case "webp" -> b.length >= 12 && startsWith(b, 0x52, 0x49, 0x46, 0x46) // RIFF
                    && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50; // WEBP
            case "avif" -> b.length >= 12
                    && b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70; // ftyp
            default -> false;
        };
    }

    private boolean startsWith(byte[] b, int... magic) {
        if (b.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((b[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }

    /**
     * SVG 是文本格式，可能夹带 {@code <script>} / 事件属性 / 伪协议，
     * 直接访问 CDN 链接时会在浏览器执行 —— 检测到即拒绝（宁可拒真，不放毒）。
     */
    private void validateSvg(byte[] bytes) {
        // SVG 一般不超过几百 KB，超大文本直接拒
        if (bytes.length > 2 * 1024 * 1024) {
            throw new BizException("SVG 文件过大，已拒绝上传");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.contains("<svg") && !text.startsWith("<?xml")) {
            throw new BizException("文件内容与扩展名 .svg 不符，已拒绝上传");
        }
        if (SVG_DANGER.matcher(text).find()) {
            throw new BizException("SVG 中包含脚本等不安全内容，已拒绝上传");
        }
    }

    public record StoredImage(String fileName,
                              String originName,
                              String githubPath,
                              String sha,
                              String cdnUrl,
                              long size,
                              String contentType,
                              Long repoId,
                              String repoName) {
    }
}
