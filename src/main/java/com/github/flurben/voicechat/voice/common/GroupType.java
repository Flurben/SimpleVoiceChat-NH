package com.github.flurben.voicechat.voice.common;

public enum GroupType {
    /** Players outside the group can not hear members, but members can hear them. */
    NORMAL("normal"),
    /** Players outside the group can hear members, and members can hear them. */
    OPEN("open"),
    /** Players outside the group can not hear members, and members can not hear them. */
    ISOLATED("isolated");

    public final String id;

    GroupType(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "message.voicechat.group_type." + id;
    }

    public String descriptionKey() {
        return "message.voicechat.group_type." + id + ".description";
    }

    public static GroupType byId(int id) {
        GroupType[] v = values();
        return id >= 0 && id < v.length ? v[id] : NORMAL;
    }
}
