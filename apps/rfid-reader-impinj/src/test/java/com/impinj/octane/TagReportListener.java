package com.impinj.octane;

public interface TagReportListener {
    void onTagReported(ImpinjReader reader, TagReport report);
}
