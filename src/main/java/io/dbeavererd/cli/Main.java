package io.dbeavererd.cli;

import picocli.CommandLine;

/** Entry point: delegates to the picocli command. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        int code = new CommandLine(new ErdCommand()).execute(args);
        System.exit(code);
    }
}
