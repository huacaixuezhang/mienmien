package com.mienmien.business.management.application.dto;

/** 创建视频面试会话时传入的「本轮面试官」席位，用于拼接角色情境快照。 */
public record VideoInterviewerSlotRequest(String role, String name) {}
