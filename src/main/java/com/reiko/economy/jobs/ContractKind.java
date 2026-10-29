package com.reiko.economy.jobs;

/**
 * What a contract asks for. Deliveries are handed in at the job board; the rest count as the work happens.
 * {@code WORK} counts actions other mods report through {@link com.reiko.economy.api.JobsApi}.
 */
public enum ContractKind {
    DELIVER("deliver"),
    BREAK("break"),
    KILL("kill"),
    FISH("fish"),
    BREED("breed"),
    WORK("work");

    private final String key;

    ContractKind(String key) {
        this.key = key;
    }

    /** The field name in job files, and part of the lang key for the contract's text. */
    public String key() {
        return key;
    }
}
