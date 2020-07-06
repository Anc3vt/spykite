package ru.ancevt.spykite.grabbers;

import ru.ancevt.spykite.Log;
import ru.ancevt.util.args.Args;
import ru.ancevt.util.string.StringUtil;
import ru.ancevt.util.system.UnixDisplay;

/**
 *
 * @author ancevt
 */
public class ErrorSimulator {

    private Args args;
    private double chance;

    public ErrorSimulator() {
        this(StringUtil.EMPTY);
    }

    public ErrorSimulator(String toparse) {
        parse(toparse);
    }

    public void parse(String toparse) {
        args = new Args(toparse);
        chance = args.getDouble("--chance", 0.5);
    }

    private boolean checkChance() {
        return Math.random() < chance;
    }

    public void checkSetUrl(Runnable function) {
        if (args.contains("--set-url")) {
            
            Log.hl.warn(UnixDisplay.format("{y}ES: setUrl?{}"));
            
            if (!checkChance()) {
                return;
            }
            Log.hl.warn(UnixDisplay.format("{y}ES: setUrl!{}"));
            function.run();
        }
    }
}
