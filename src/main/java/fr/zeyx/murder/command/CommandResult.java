package fr.zeyx.murder.command;

public enum CommandResult {
    SUCCESS(true),
    FAILURE(false),
    INVALID_USAGE(false);

    private final boolean success;

    CommandResult(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
