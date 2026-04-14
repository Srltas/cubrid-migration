package com.cmt.e2e.framework.template;

import java.nio.file.Path;

/**
 * {@link ScriptTemplateResolver#resolve()}의 반환 값.
 *
 * <p>치환 완료된 스크립트 파일 경로와, CMT Console이 Dump 마이그레이션 시
 * 생성하는 출력 디렉토리명을 함께 제공합니다.
 *
 * <p>CMT Console은 파일 타겟 마이그레이션 시 다음 형식의 디렉토리를 생성합니다:
 * <pre>
 * {file_repository.dir}/{migration.name}/{schema}/...
 * 예: ./output/CUBRID_demodb_202604062341/PUBLIC/...
 * </pre>
 *
 * <p>따라서 본 객체는 스크립트가 정의한 migration 이름
 * ({@code <migration name="...">})을 그대로 제공하고,
 * {@code CmtTestContext.migrationOutput()}이 이를 실제 출력 디렉토리명으로 사용합니다.
 */
public class ResolvedScript {

    private final Path scriptPath;
    private final String migrationName;

    ResolvedScript(Path scriptPath, String migrationName) {
        this.scriptPath = scriptPath;
        this.migrationName = migrationName;
    }

    /** 치환이 완료된 스크립트 파일의 경로 */
    public Path scriptPath() {
        return scriptPath;
    }

    /**
     * CMT Console이 파일 타겟 마이그레이션 시 생성하는 출력 디렉토리명.
     * <p>형식: {@code migration/@name}
     * <br>예: {@code "CUBRID_demodb_202604062341"}
     *
     * <p>실제 출력 경로는 {@code {file_repository.dir}/{migrationName}/{schema}} 이며,
     * {@code CmtTestContext.migrationOutput(migrationName, schema)}이 이를 계산합니다.
     */
    public String migrationName() {
        return migrationName;
    }
}
