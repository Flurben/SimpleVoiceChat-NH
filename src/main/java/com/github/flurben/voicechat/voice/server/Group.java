package com.github.flurben.voicechat.voice.server;

import java.util.UUID;

import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;

public class Group {

    public final UUID id;
    public final String name;
    public final String password;
    public final GroupType type;
    public final boolean persistent;

    public Group(UUID id, String name, String password, GroupType type, boolean persistent) {
        this.id = id;
        this.name = name;
        this.password = password == null || password.isEmpty() ? null : password;
        this.type = type;
        this.persistent = persistent;
    }

    public boolean isOpen() {
        return type == GroupType.OPEN;
    }

    public ClientGroup toClientGroup() {
        return new ClientGroup(id, name, password != null, type);
    }
}
