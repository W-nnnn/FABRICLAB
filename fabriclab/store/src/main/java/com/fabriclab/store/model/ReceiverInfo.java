package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 收货信息三项校验（FR-O-01）：均去首尾空白后必填，长度上限分别为
 * 收货人 50、联系电话 30、详细地址 255。本期使用单一完整地址文本，
 * 不拆省市区，不做手机号位数硬编码；只约束字符集——号码、区号、分机
 * 与分隔符之外的内容一律拒绝，避免把随手填的字当成电话收下。
 */
public record ReceiverInfo(String name, String phone, String address) {

    public static final int NAME_MAX = 50;
    public static final int PHONE_MAX = 30;
    public static final int ADDRESS_MAX = 255;

    /** 字段名：既做提示文案前缀，也用于回填时定位出错字段。 */
    public static final String NAME = "收货人";
    public static final String PHONE = "联系电话";
    public static final String ADDRESS = "详细地址";

    /** 允许数字、区号加号、空格与常见分隔符，以及中文「转」作为分机标记。 */
    private static final Pattern PHONE_CHARS = Pattern.compile("^[0-9+\\-\\s()转]+$");

    public static ReceiverInfo parse(String name, String phone, String address) {
        return new ReceiverInfo(
                require(NAME, name, NAME_MAX),
                phone(phone),
                require(ADDRESS, address, ADDRESS_MAX));
    }

    /** 由校验消息反推字段：name / phone / address，非字段类错误返回 null。 */
    public static String fieldOf(String message) {
        if (message == null) {
            return null;
        }
        if (message.startsWith(NAME)) {
            return "name";
        }
        if (message.startsWith(PHONE)) {
            return "phone";
        }
        if (message.startsWith(ADDRESS)) {
            return "address";
        }
        return null;
    }

    private static String phone(String raw) {
        String trimmed = require(PHONE, raw, PHONE_MAX);
        if (!PHONE_CHARS.matcher(trimmed).matches()) {
            throw new BusinessException(PHONE + "格式不正确，请填写号码与区号");
        }
        return trimmed;
    }

    private static String require(String field, String raw, int max) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(field + "不能为空");
        }
        if (trimmed.length() > max) {
            throw new BusinessException(field + "最长 " + max + " 个字符");
        }
        return trimmed;
    }
}
