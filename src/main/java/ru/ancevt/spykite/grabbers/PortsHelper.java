package ru.ancevt.spykite.grabbers;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ancevt
 */
class PortsHelper {

    private static final List<PortWrapper> ports = new ArrayList<>();

    public static void addPort(int port) {
        ports.add(new PortWrapper(port));
    }

    public static void removePort(int port) {
        for(int i = 0; i < ports.size(); i ++) {
            if(ports.get(i).port == port) {
                ports.remove(i);
            }
        }
    }

    public static boolean contains(int port) {
        for(int i = 0; i < ports.size(); i ++) {
            if(ports.get(i).port == port) {
                return true;
            }
        }
        return false;
    }
}

class PortWrapper {

    public final int port;

    public PortWrapper(int port) {
        this.port = port;
    }
}
