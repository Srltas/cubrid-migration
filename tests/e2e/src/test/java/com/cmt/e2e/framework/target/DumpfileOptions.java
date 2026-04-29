package com.cmt.e2e.framework.target;

/**
 * Settings that go into the dump-file target block of CMT {@code db.conf}.
 *
 * @param filePrefix       value of {@code <target>.file_prefix} — typically
 *                         the source DB nickname (e.g. {@code "XE"},
 *                         {@code "demodb"})
 * @param oneTableOneFile  when {@code true}, one dump file per table;
 *                         when {@code false}, all tables share one dump
 */
public record DumpfileOptions(String filePrefix, boolean oneTableOneFile) {
    public DumpfileOptions {
        if (filePrefix == null || filePrefix.isBlank()) {
            throw new IllegalArgumentException("filePrefix must not be blank");
        }
    }
}
