package org.jetbrains.kotlin.tools.gradleimportcmd;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class GradleModelBuilderOverheadContainer {

    private static Map<String, Long> overhead;

    static {
        overhead = new HashMap<>();
    }

    public static void reportOutput(String line) {
        if (line != null && line.contains("<ij_msg_gr>Performance statistics<ij_msg_gr>")) {
            synchronized (overhead) {
                String marker = "': service ";
                String[] lineParts = line.substring(line.indexOf(marker) + marker.length()).split(" ");
                String service = lineParts[0];
                long duration = Long.parseLong(lineParts[4]);
                Long currentDuration = overhead.get(service);
                if (currentDuration != null) {
                    duration += currentDuration;
                }
                overhead.put(service, duration);
            }
        }
    }

    public static Map<String, Long> getOverhead() {
        synchronized (overhead) {
            return new TreeMap<>(overhead);
        }
    }
}
