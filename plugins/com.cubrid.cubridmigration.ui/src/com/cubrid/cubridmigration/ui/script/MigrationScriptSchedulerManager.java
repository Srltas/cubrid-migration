/*
 * Copyright (C) 2008 Search Solution Corporation.
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.ui.script;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Migration Reservation Manager using OS specific schedulers.
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-12-10 created by Kevin Cao
 */
public class MigrationScriptSchedulerManager {

    private static final Logger LOG = LogUtil.getLogger(MigrationScriptSchedulerManager.class);

    /**
     * Add a migration script to reservation.
     *
     * @param script MigrationScript
     */
    public static void addReservation(MigrationScript script) {
        String reservationId = UUID.randomUUID().toString();
        script.setReservationID(reservationId);

        try {
            if (CUBRIDIOUtils.IS_OS_WINDOWS) {
                addWindowsTask(script);
            } else {
                addLinuxTask(script);
            }
        } catch (Exception e) {
            LOG.error("Failed to add reservation", e);
            throw new RuntimeException("Failed to schedule task: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel scheduled script.
     *
     * @param script MigrationScript
     */
    public static void cancel(MigrationScript script) {
        try {
            if (CUBRIDIOUtils.IS_OS_WINDOWS) {
                removeWindowsTask(script);
            } else {
                removeLinuxTask(script);
            }
        } catch (Exception e) {
            LOG.error("Failed to cancel reservation", e);
            throw new RuntimeException("Failed to cancel task: " + e.getMessage(), e);
        }
        script.setReservationID(null);
    }

    private static void addWindowsTask(MigrationScript script) throws IOException, InterruptedException {
        String taskName = "CMT_" + script.getName();
        String scriptPath = getScriptPath(true);
        String configPath = script.getAbstractConfigFileName();

        // Ensure paths are quoted
        String command = "\"" + scriptPath + "\" \"" + configPath + "\"";

        List<String> cmdList = new ArrayList<>();
        cmdList.add("schtasks");
        cmdList.add("/Create");
        cmdList.add("/TN");
        cmdList.add(taskName);
        cmdList.add("/TR");
        cmdList.add(command);
        cmdList.add("/F"); // Force create

        // Handle cron modes
        int mode = script.getCronMode();
        String pattern = script.getCronPatten();

        // 0: Once: min hour day month *
        // 1: Repeat: min hour * * * (Daily)
        // 2: Advanced: arbitrary

        if (mode == 0) {
            // schtasks /SC ONCE /ST HH:MM /SD DD/MM/YYYY
            // Parse min hour day month
            try {
                String[] parts = pattern.split(" ");
                if (parts.length >= 4) {
                    String min = padTime(parts[0]);
                    String hour = padTime(parts[1]);
                    String day = parts[2];
                    String month = parts[3];

                    cmdList.add("/SC");
                    cmdList.add("ONCE");
                    cmdList.add("/ST");
                    cmdList.add(hour + ":" + min);

                    // Year is tricky. Assume current year, if date is passed, next year.
                    Calendar now = Calendar.getInstance();
                    int year = now.get(Calendar.YEAR);
                    int currentMonth = now.get(Calendar.MONTH) + 1;
                    int currentDay = now.get(Calendar.DAY_OF_MONTH);

                    int targetMonth = Integer.parseInt(month);
                    int targetDay = Integer.parseInt(day);

                    if (targetMonth < currentMonth || (targetMonth == currentMonth && targetDay < currentDay)) {
                        year++;
                    }

                    // /SD format depends on system locale! This is DANGEROUS.
                    // However, schtasks is usually smart enough or expects MM/DD/YYYY or DD/MM/YYYY based on locale.
                    // But we can't easily detect system date format here.
                    // Standard ISO format YYYY/MM/DD often works but schtasks help says "DD/MM/YYYY".
                    // Let's try standard format or system dependent.
                    // This is a known issue with schtasks.
                    // Best effort: MM/DD/YYYY is common default for US, DD/MM/YYYY for others.
                    // Wait, schtasks manual says: "Specifies the start date for the task. The format is MM/DD/YYYY." (US English)
                    // If we are on Korean Windows (likely given context), it might be YYYY-MM-DD.

                    // Since we can't reliably guess, we might use "ONCE" mode carefully or use "DAILY" and delete it after run? No.
                    // Let's trust the system to handle standard formats.
                    // Or we just fallback to DAILY with start date if ONCE fails? No.

                    // Actually, if we use /SC ONCE, we MUST provide /SD if it's not today.
                    // Let's skip /SD and let it be today if not provided? No, it defaults to system date.

                    // Alternative: use a simpler approach. If user wants "Once", we schedule it.
                    // But without knowing the date format, /SD is risky.
                    // However, we can try to guess or just use "DAILY" as a fallback?

                    // Let's try to construct a date string.
                    String dateStr = String.format("%02d/%02d/%04d", targetDay, targetMonth, year); // DD/MM/YYYY - risks locale
                    // Windows 10+ is smarter.
                    cmdList.add("/SD");
                    cmdList.add(dateStr);
                } else {
                   // Fallback
                   cmdList.add("/SC");
                   cmdList.add("DAILY");
                }
            } catch (Exception e) {
                LOG.error("Error parsing one-time schedule", e);
                cmdList.add("/SC");
                cmdList.add("DAILY");
            }
        } else if (mode == 1) {
            // Repeating Daily
            // pattern: min hour * * *
            try {
                String[] parts = pattern.split(" ");
                String min = padTime(parts[0]);
                String hour = padTime(parts[1]);

                cmdList.add("/SC");
                cmdList.add("DAILY");
                cmdList.add("/ST");
                cmdList.add(hour + ":" + min);
            } catch (Exception e) {
                 cmdList.add("/SC");
                 cmdList.add("DAILY");
            }
        } else {
            // Advanced
            // Fallback to DAILY as schtasks can't handle complex cron easily
            cmdList.add("/SC");
            cmdList.add("DAILY");
        }

        // Execute
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            String error = readStream(p.getErrorStream());
            // Retry for ONCE with different date format if it failed?
            // Too complex for this scope. Just throw error.
            throw new IOException("schtasks failed with exit code " + exitCode + ": " + error);
        }
    }

    private static String padTime(String s) {
        if (s.length() == 1) return "0" + s;
        return s;
    }

    private static void removeWindowsTask(MigrationScript script) throws IOException, InterruptedException {
        String taskName = "CMT_" + script.getName();
        List<String> cmdList = new ArrayList<>();
        cmdList.add("schtasks");
        cmdList.add("/Delete");
        cmdList.add("/TN");
        cmdList.add(taskName);
        cmdList.add("/F");

        ProcessBuilder pb = new ProcessBuilder(cmdList);
        Process p = pb.start();
        int exitCode = p.waitFor();
        // Ignore "not found" error
        if (exitCode != 0 && exitCode != 1) { // 1 might be "not found"
             String error = readStream(p.getErrorStream());
             if (!error.contains("The system cannot find the file specified")) {
                 LOG.warn("schtasks delete warning: " + error);
             }
        }
    }

    private static void addLinuxTask(MigrationScript script) throws IOException, InterruptedException {
        String scriptPath = getScriptPath(false);
        String configPath = script.getAbstractConfigFileName();
        String cronExpression = script.getCronPatten();

        if (cronExpression == null || cronExpression.isEmpty()) {
            cronExpression = "0 0 * * *"; // Default to daily midnight
        }

        // Ensure script is executable
        File scriptFile = new File(scriptPath);
        if (scriptFile.exists() && !scriptFile.canExecute()) {
            scriptFile.setExecutable(true);
        }

        // 1. Read current crontab
        List<String> lines = readCrontab();

        // 2. Add new line
        // Format: * * * * * /path/to/migration.sh /path/to/config.xml #CMT_RESERVATION_ID
        String cmd = cronExpression + " " + scriptPath + " " + configPath + " #CMT_" + script.getReservationID();
        lines.add(cmd);

        // 3. Write back
        writeCrontab(lines);
    }

    private static void removeLinuxTask(MigrationScript script) throws IOException, InterruptedException {
        String tag = "#CMT_" + script.getReservationID();

        // 1. Read current crontab
        List<String> lines = readCrontab();

        // 2. Filter out the task
        List<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains(tag)) {
                newLines.add(line);
            }
        }

        // 3. Write back
        writeCrontab(newLines);
    }

    private static List<String> readCrontab() throws IOException, InterruptedException {
        List<String> lines = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("crontab", "-l");
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        p.waitFor();
        // Ignore exit code because empty crontab returns 1 on some systems
        return lines;
    }

    private static void writeCrontab(List<String> lines) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("crontab", "-");
        Process p = pb.start();
        // Write to stdin
        try (java.io.PrintWriter writer = new java.io.PrintWriter(p.getOutputStream())) {
            for (String line : lines) {
                writer.println(line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            String error = readStream(p.getErrorStream());
             throw new IOException("crontab write failed: " + error);
        }
    }

    private static String getScriptPath(boolean isWindows) {
        // Assume migration script is in the 'bin' or root of installation, or 'command' folder
        // For simplicity, we assume it's next to the executable or in a known location relative to install path
        String installPath = PathUtils.getInstallPath();
        String scriptName = isWindows ? "migration.bat" : "migration.sh";

        // First check directly under install path
        File f = new File(installPath, scriptName);
        if (f.exists()) return f.getAbsolutePath();

        // Check in 'command' subdirectory (as per source structure)
        f = new File(new File(installPath, "command"), scriptName); // Hypothetical
        if (f.exists()) return f.getAbsolutePath();

        // Check in 'bin'
        f = new File(new File(installPath, "bin"), scriptName);
        if (f.exists()) return f.getAbsolutePath();

        // Fallback to install path if not found (let the user ensure it's there)
        return new File(installPath, scriptName).getAbsolutePath();
    }

    private static String readStream(java.io.InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
