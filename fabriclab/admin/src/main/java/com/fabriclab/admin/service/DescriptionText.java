package com.fabriclab.admin.service;

/**
 * 设计稿的「面料 / 克重 / 版型 / 工艺」四个输入与 DDL 的 description 单字段之间的
 * 无损编解码。只识别行首标签，识别不到的原文整段留在 extra，
 * 因此既有商品描述在编辑保存后不会被改写。
 */
public final class DescriptionText {

    public record Fields(String fabric, String weight, String fit, String craft, String extra) {}

    private static final String[] LABELS = {"面料：", "克重：", "版型：", "工艺："};
    private static final String[] EN_LABELS = {"Fabric: ", "Gauge: ", "Fit: ", "Craft: "};

    private DescriptionText() {}

    public static Fields parse(String raw) {
        String[] found = {"", "", "", ""};
        StringBuilder extra = new StringBuilder();
        if (raw != null && !raw.isBlank()) {
            for (String line : raw.split("\r?\n", -1)) {
                int hit = -1;
                for (int i = 0; i < LABELS.length; i++) {
                    if (line.startsWith(LABELS[i])) { hit = i; break; }
                    if (line.startsWith(EN_LABELS[i])) { hit = i; break; }
                }
                if (hit >= 0) {
                    String value = line.substring(line.indexOf('：') >= 0
                            ? line.indexOf('：') + 1 : line.indexOf(':') + 1).trim();
                    found[hit] = value;
                } else if (!line.isBlank() || extra.length() > 0) {
                    if (extra.length() > 0) extra.append('\n');
                    extra.append(line);
                }
            }
        }
        return new Fields(found[0], found[1], found[2], found[3], extra.toString());
    }

    public static String compose(Fields f) {
        String[] values = {f.fabric(), f.weight(), f.fit(), f.craft()};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < LABELS.length; i++) {
            if (values[i] != null && !values[i].isBlank()) {
                if (out.length() > 0) out.append('\n');
                out.append(LABELS[i]).append(values[i].trim());
            }
        }
        if (f.extra() != null && !f.extra().isBlank()) {
            if (out.length() > 0) out.append('\n');
            out.append(f.extra().trim());
        }
        return out.toString();
    }

    /** 列表副行：优先取描述里的面料，其次取摘要，都不存在时为空。 */
    public static String fabricLine(Fields f, String summary) {
        if (f.fabric() != null && !f.fabric().isBlank()) return f.fabric();
        return summary == null ? "" : summary;
    }
}
