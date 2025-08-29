package net.minecraft;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.util.MemoryReserve;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

public class CrashReport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final Throwable exception;
    private final List<CrashReportSystemDetails> details = Lists.newArrayList();
    @Nullable
    private Path saveFile;
    private boolean trackingStackTrace = true;
    private StackTraceElement[] uncategorizedStackTrace = new StackTraceElement[0];
    private final SystemReport systemReport = new SystemReport();

    public CrashReport(String s, Throwable throwable) {
        this.title = s;
        this.exception = throwable;
        this.systemReport.setDetail("CraftBukkit Information", new org.bukkit.craftbukkit.CraftCrashReport()); // CraftBukkit
    }

    public String getTitle() {
        return this.title;
    }

    public Throwable getException() {
        return this.exception;
    }

    public String getDetails() {
        StringBuilder stringbuilder = new StringBuilder();

        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public void getDetails(StringBuilder stringbuilder) {
        if ((this.uncategorizedStackTrace == null || this.uncategorizedStackTrace.length <= 0) && !this.details.isEmpty()) {
            this.uncategorizedStackTrace = (StackTraceElement[]) ArrayUtils.subarray(((CrashReportSystemDetails) this.details.get(0)).getStacktrace(), 0, 1);
        }

        if (this.uncategorizedStackTrace != null && this.uncategorizedStackTrace.length > 0) {
            stringbuilder.append("-- Head --\n");
            stringbuilder.append("Thread: ").append(Thread.currentThread().getName()).append("\n");
            stringbuilder.append("Stacktrace:\n");

            for (StackTraceElement stacktraceelement : this.uncategorizedStackTrace) {
                stringbuilder.append("\t").append("at ").append(stacktraceelement);
                stringbuilder.append("\n");
            }

            stringbuilder.append("\n");
        }

        for (CrashReportSystemDetails crashreportsystemdetails : this.details) {
            crashreportsystemdetails.getDetails(stringbuilder);
            stringbuilder.append("\n\n");
        }

        this.systemReport.appendToCrashReportString(stringbuilder);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Throwable throwable = this.exception;

        if (throwable.getMessage() == null) {
            if (throwable instanceof NullPointerException) {
                throwable = new NullPointerException(this.title);
            } else if (throwable instanceof StackOverflowError) {
                throwable = new StackOverflowError(this.title);
            } else if (throwable instanceof OutOfMemoryError) {
                throwable = new OutOfMemoryError(this.title);
            }

            throwable.setStackTrace(this.exception.getStackTrace());
        }

        String s;

        try {
            stringwriter = new StringWriter();
            printwriter = new PrintWriter(stringwriter);
            throwable.printStackTrace(printwriter);
            s = stringwriter.toString();
        } finally {
            IOUtils.closeQuietly(stringwriter);
            IOUtils.closeQuietly(printwriter);
        }

        return s;
    }

    public String getFriendlyReport(ReportType reporttype, List<String> list) {
        StringBuilder stringbuilder = new StringBuilder();

        reporttype.appendHeader(stringbuilder, list);
        stringbuilder.append("Time: ");
        stringbuilder.append(CrashReport.DATE_TIME_FORMATTER.format(ZonedDateTime.now()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.title);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; ++i) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.getDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public String getFriendlyReport(ReportType reporttype) {
        return this.getFriendlyReport(reporttype, List.of());
    }

    @Nullable
    public Path getSaveFile() {
        return this.saveFile;
    }

    public boolean saveToFile(Path path, ReportType reporttype, List<String> list) {
        if (this.saveFile != null) {
            return false;
        } else {
            try {
                if (path.getParent() != null) {
                    FileUtils.createDirectoriesSafe(path.getParent());
                }

                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    writer.write(this.getFriendlyReport(reporttype, list));
                }

                this.saveFile = path;
                return true;
            } catch (Throwable throwable) {
                CrashReport.LOGGER.error("Could not save crash report to {}", path, throwable);
                return false;
            }
        }
    }

    public boolean saveToFile(Path path, ReportType reporttype) {
        return this.saveToFile(path, reporttype, List.of());
    }

    public SystemReport getSystemReport() {
        return this.systemReport;
    }

    public CrashReportSystemDetails addCategory(String s) {
        return this.addCategory(s, 1);
    }

    public CrashReportSystemDetails addCategory(String s, int i) {
        CrashReportSystemDetails crashreportsystemdetails = new CrashReportSystemDetails(s);

        if (this.trackingStackTrace) {
            int j = crashreportsystemdetails.fillInStackTrace(i);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int k = astacktraceelement.length - j;

            if (k < 0) {
                CrashReport.LOGGER.error("Negative index in crash report handler ({}/{})", astacktraceelement.length, j);
            }

            if (astacktraceelement != null && 0 <= k && k < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[k];
                if (astacktraceelement.length + 1 - j < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - j];
                }
            }

            this.trackingStackTrace = crashreportsystemdetails.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (astacktraceelement != null && astacktraceelement.length >= j && 0 <= k && k < astacktraceelement.length) {
                this.uncategorizedStackTrace = new StackTraceElement[k];
                System.arraycopy(astacktraceelement, 0, this.uncategorizedStackTrace, 0, this.uncategorizedStackTrace.length);
            } else {
                this.trackingStackTrace = false;
            }
        }

        this.details.add(crashreportsystemdetails);
        return crashreportsystemdetails;
    }

    public static CrashReport forThrowable(Throwable throwable, String s) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        CrashReport crashreport;

        if (throwable instanceof ReportedException reportedexception) {
            crashreport = reportedexception.getReport();
        } else {
            crashreport = new CrashReport(s, throwable);
        }

        return crashreport;
    }

    public static void preload() {
        MemoryReserve.allocate();
        (new CrashReport("Don't panic!", new Throwable())).getFriendlyReport(ReportType.CRASH);
    }
}
