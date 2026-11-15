package com.fabriclab.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * 商品图片落盘（FR-A-07）：把上传文件写进上传根目录的 images/products/{SKU}/，
 * 同时镜像一份到用户端静态目录，保证前后台看到同一批图。
 * 库里保存的仍是既有约定的站内相对路径 images/products/{SKU}/文件名，
 * 因此不需要改 DDL，用户端照旧按 static 路径读取。
 */
@Service
public class ProductImageStorage {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;
    private final Path storeMirror;

    public ProductImageStorage(@Value("${fabriclab.uploads.dir:uploads}") String dir,
                               @Value("${fabriclab.uploads.store-mirror:../store/src/main/resources/static}") String mirror) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.storeMirror = mirror == null || mirror.isBlank() ? null
                : Paths.get(mirror).toAbsolutePath().normalize();
    }

    /** 上传根目录（绝对路径），供静态资源映射使用。 */
    public Path root() {
        return root;
    }

    /** 保存一张图片，返回可直接写进 product.cover_image / product_image.image_path 的相对路径。 */
    public String store(String sku, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("size");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(type)) {
            throw new IllegalArgumentException("type");
        }
        String ext = switch (type) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String dirName = sku(sku);
        String name = "u" + System.currentTimeMillis()
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF)) + "." + ext;
        Path target = root.resolve("images/products").resolve(dirName).resolve(name);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (storeMirror != null) {
                Path mirrorDir = storeMirror.resolve("images/products").resolve(dirName);
                Files.createDirectories(mirrorDir);
                Files.copy(target, mirrorDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("store-failed", e);
        }
        return "images/products/" + dirName + "/" + name;
    }

    /** 路径是否存在：先查上传目录，再查打包进 classpath 的既有静态图。 */
    public boolean exists(String relativePath) {
        String p = normalize(relativePath);
        if (p == null) return false;
        if (Files.isRegularFile(root.resolve(p))) return true;
        return new ClassPathResource("static/" + p).exists();
    }

    /**
     * 该款号目录下已有的图片，返回站内相对路径。上传目录与打包静态目录一起扫，
     * 这样「从相册选择」对早期随课程素材一起进库的图同样可用。
     */
    public List<String> list(String sku) {
        String dirName;
        try {
            dirName = sku(sku);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        String prefix = "images/products/" + dirName + "/";
        List<String> out = new ArrayList<>();
        Path dir = root.resolve("images/products").resolve(dirName);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(Files::isRegularFile)
                        .map(f -> f.getFileName().toString())
                        .filter(ProductImageStorage::isImage)
                        .forEach(n -> out.add(prefix + n));
            } catch (IOException ignored) {
            }
        }
        try {
            Resource[] found = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:static/images/products/" + dirName + "/*");
            for (Resource r : found) {
                String name = r.getFilename();
                if (name != null && isImage(name) && !out.contains(prefix + name)) {
                    out.add(prefix + name);
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(out);
        return out;
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    /** 去掉前导斜杠并拒绝越界路径；不合法返回 null。 */
    private String normalize(String raw) {
        if (raw == null) return null;
        String v = raw.trim().replace('\\', '/');
        while (v.startsWith("/")) v = v.substring(1);
        if (v.isEmpty() || v.contains("..") || v.contains(":")) return null;
        return v;
    }

    private String sku(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.length() > 40 || !v.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("sku");
        }
        return v;
    }
}
