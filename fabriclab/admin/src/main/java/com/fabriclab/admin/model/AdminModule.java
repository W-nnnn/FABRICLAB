package com.fabriclab.admin.model;

/**
 * 后台模块入口。控制台只呈现模块范围与所属需求；
 * implemented 表示该入口是否已在当前版本交付，未交付的入口不提供链接、不呈现任何数字。
 */
public record AdminModule(String name, String path, String scope, String requirement, boolean implemented) {
}
