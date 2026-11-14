package com.fabriclab.admin.model;

/**
 * 相册条目视图：类型与来源许可由 {@code AlbumText} 从 alt_text 解出，
 * cover 标记当前封面是否就是这张图。
 */
public record AlbumImage(Long id, String path, String kind, String credit, String alt,
                         int sortOrder, boolean cover) {
}
