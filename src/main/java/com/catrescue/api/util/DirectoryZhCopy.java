package com.catrescue.api.util;

import java.util.Map;

/**
 * Chinese copy for the static rescue / TNR directory entries, keyed by entry id.
 * Real organization names and street addresses stay as registered (null here); only platform-written
 * descriptions and generic placeholder entries are translated.
 */
public final class DirectoryZhCopy {

    public record ZhCopy(String name, String shortDescription, String streetAddress) {
    }

    private static final Map<String, ZhCopy> COPY = Map.of(
            // Rescue directory
            "sj-acs", new ZhCopy(null, "市政动物收容与救助协调，适用于受伤或处境危险的动物", null),
            "hssv", new ZhCopy(null, "南湾地区的救助支持、领养与社区猫指导", null),
            "sjacc", new ZhCopy(
                    "圣克拉拉县动物服务（区域资源）",
                    "县级联络点与合作救助组织；派车前请先确认管辖范围",
                    "服务范围因城市与合作收容所而异"
            ),
            // TNR directory
            "sj-city", new ZhCopy(null, "市政收容所：可咨询社区猫、TNR 与低价绝育项目", null),
            "hscc", new ZhCopy(null, "南湾 TNR 支持与诊所服务；请确认流浪猫 / 社区猫项目", null),
            "sbac", new ZhCopy(
                    "圣克拉拉县地区：请联系当地收容所与救助团体",
                    "全县范围：请核实当前的 TNR 合作与绝育补贴项目",
                    "服务范围因城市与合作收容所而异"
            )
    );

    private DirectoryZhCopy() {
    }

    public static ZhCopy forId(String id) {
        ZhCopy c = id == null ? null : COPY.get(id);
        return c != null ? c : new ZhCopy(null, null, null);
    }
}
