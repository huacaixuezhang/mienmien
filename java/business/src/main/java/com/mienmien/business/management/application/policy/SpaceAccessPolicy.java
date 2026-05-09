package com.mienmien.business.management.application.policy;

import com.mienmien.business.management.domain.model.Space;

public interface SpaceAccessPolicy {
    /**
     * 空间存在、归属当前用户且非归档；允许回收站中的只读场景。
     */
    Space requireReadableSpaceForActor(String spaceId, String actorUserId);

    /**
     * 空间可用（ACTIVE）且归属当前用户，用于新增与修改类操作。
     */
    Space requireWritableSpaceForActor(String spaceId, String actorUserId);
}
