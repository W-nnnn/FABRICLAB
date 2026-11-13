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

    /** 允许数字、区号加号、空格与常见分隔符，以及中文「转」作为分机标记。 */
    private static final Pattern PHONE_CHARS = Pattern.compile("^[0-9+\\-\\s()转]+$");

    public static ReceiverInfo parse(String name, String phone, String address) {
        return new ReceiverInfo(
                require("收货人", name, NAME_MAX),
                phone(phone),
                require("详细地址", address, ADDRESS_MAX));
    }

    private static String phone(String raw) {
        String trimmed = require("联系电话", raw, PHONE_MAX);
        if (!PHONE_CHARS.matcher(trimmed).matches()) {
            throw new BusinessException("联系电话格式不正确，请填写号码与区号");
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
