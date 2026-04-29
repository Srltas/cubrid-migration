package com.cmt.e2e.framework.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

    @TempDir Path tmp;

    @AfterEach
    void clearUpdateFlag() {
        System.clearProperty(SnapshotStore.UPDATE_PROP);
    }

    @Test
    void matches_when_content_equal() throws IOException {
        Path snap = tmp.resolve("a.txt");
        Files.writeString(snap, "hello\n");
        SnapshotStore.match(snap, "hello\n");   // no exception
    }

    @Test
    void throws_on_mismatch_with_first_diff_line() throws IOException {
        Path snap = tmp.resolve("a.txt");
        Files.writeString(snap, "line1\nline2\nline3\n");

        assertThatThrownBy(() -> SnapshotStore.match(snap, "line1\nDIFFERENT\nline3\n"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("line 2")
            .hasMessageContaining("line2")
            .hasMessageContaining("DIFFERENT")
            .hasMessageContaining("snapshot.update");
    }

    @Test
    void throws_on_missing_snapshot_with_update_hint() {
        Path snap = tmp.resolve("missing.txt");

        assertThatThrownBy(() -> SnapshotStore.match(snap, "anything"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Snapshot missing")
            .hasMessageContaining("snapshot.update");
    }

    @Test
    void update_mode_writes_file_and_creates_parents() throws IOException {
        Path snap = tmp.resolve("nested/dir/snap.txt");
        System.setProperty(SnapshotStore.UPDATE_PROP, "true");

        SnapshotStore.match(snap, "captured\n");

        assertThat(Files.exists(snap)).isTrue();
        assertThat(Files.readString(snap)).isEqualTo("captured\n");
    }

    @Test
    void update_mode_overwrites_existing_file() throws IOException {
        Path snap = tmp.resolve("snap.txt");
        Files.writeString(snap, "old\n");
        System.setProperty(SnapshotStore.UPDATE_PROP, "true");

        SnapshotStore.match(snap, "new\n");

        assertThat(Files.readString(snap)).isEqualTo("new\n");
    }

    @Test
    void update_mode_does_not_throw_on_missing_dir() throws IOException {
        Path snap = tmp.resolve("a/b/c/d/x.txt");
        System.setProperty(SnapshotStore.UPDATE_PROP, "true");

        SnapshotStore.match(snap, "x\n");   // no exception, dirs created

        assertThat(Files.exists(snap)).isTrue();
    }
}
