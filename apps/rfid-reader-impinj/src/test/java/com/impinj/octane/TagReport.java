package com.impinj.octane;

import java.util.List;

public class TagReport {

    private final List<Tag> tags;

    public TagReport(List<Tag> tags) {
        this.tags = tags;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public static TagReport single(String epc, short antennaPort, double peakRssiInDbm) {
        return new TagReport(List.of(new Tag(epc, antennaPort, peakRssiInDbm)));
    }
}
