package com.thordata.sdk.errors;

import com.thordata.sdk.SecretRedactor;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Stage-aware, redacted Web Scraper failure. */
public class ThorDataWebScraperException extends ThorDataException {
    private static final Pattern TASK_ID = Pattern.compile("^[\\w-]{1,200}$");
    private static final Pattern SPIDER_ID = Pattern.compile("^[\\w-]{1,120}$");
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("invalid_parameters", "Web Scraper parameters are invalid."),
            Map.entry("submission_unknown", "Web Scraper submission outcome is unknown."),
            Map.entry("submission_failed", "Web Scraper submission was rejected."),
            Map.entry("poll_failed", "Web Scraper task status could not be verified."),
            Map.entry("scrape_failed", "Web Scraper returned only failed records or the task failed."),
            Map.entry("download_url_invalid", "Web Scraper returned an invalid download address."),
            Map.entry("download_failed", "Web Scraper results could not be downloaded."),
            Map.entry("invalid_result", "Web Scraper results are not valid JSON records."),
            Map.entry("wait_timeout", "Web Scraper exceeded the maximum wait time."),
            Map.entry("protocol_error", "Web Scraper returned an incompatible response."),
            Map.entry("authentication_failed", "Web Scraper authentication failed."),
            Map.entry("permission_denied", "Web Scraper permission was denied."),
            Map.entry("insufficient_balance", "Web Scraper account balance is insufficient."),
            Map.entry("request_failed", "Web Scraper request failed."));

    private final String errorCode;
    private final String stage;
    private final String taskId;
    private final String spiderId;
    private final String reason;
    private final String serviceCode;

    public ThorDataWebScraperException(String errorCode, String stage, String taskId, String spiderId,
                                       Object reason, Object serviceCode, Set<String> secrets) {
        super(buildMessage(errorCode, stage, taskId, reason, secrets));
        this.errorCode = MESSAGES.containsKey(errorCode) ? errorCode : "request_failed";
        this.stage = stage;
        this.taskId = safeTaskId(taskId, secrets);
        this.spiderId = safeSpiderId(spiderId);
        String safeReason = SecretRedactor.redactText(reason, secrets);
        String safeServiceCode = SecretRedactor.redactText(serviceCode, secrets);
        this.reason = safeReason.isBlank() ? null : safeReason;
        this.serviceCode = safeServiceCode.isBlank() ? null : safeServiceCode;
    }

    public ThorDataWebScraperException(String errorCode, String stage, String taskId, String spiderId) {
        this(errorCode, stage, taskId, spiderId, null, null, Set.of());
    }

    private static String buildMessage(String errorCode, String stage, String taskId, Object reason,
                                       Set<String> secrets) {
        String code = MESSAGES.containsKey(errorCode) ? errorCode : "request_failed";
        StringBuilder message = new StringBuilder(MESSAGES.get(code));
        String detail = SecretRedactor.redactText(reason, secrets);
        if (!detail.isBlank()) {
            message.append(' ').append(detail);
        }
        String safeTaskId = safeTaskId(taskId, secrets);
        if (safeTaskId != null) {
            message.append(" Task ID: ").append(safeTaskId).append('.');
        }
        return message.toString();
    }

    private static String safeTaskId(String value, Set<String> secrets) {
        if (value == null || !TASK_ID.matcher(value).matches()) {
            return null;
        }
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank() && value.contains(secret)) {
                return null;
            }
        }
        return value;
    }

    private static String safeSpiderId(String value) {
        return value != null && SPIDER_ID.matcher(value).matches() ? value : null;
    }

    public String errorCode() {
        return errorCode;
    }

    public String stage() {
        return stage;
    }

    public String taskId() {
        return taskId;
    }

    public String spiderId() {
        return spiderId;
    }

    public String reason() {
        return reason;
    }

    public String serviceCode() {
        return serviceCode;
    }
}
