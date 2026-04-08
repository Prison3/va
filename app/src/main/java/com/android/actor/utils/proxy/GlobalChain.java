package com.android.actor.utils.proxy;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.shell.AsyncShell;
import com.android.actor.utils.shell.Libsu;

public class GlobalChain {

    private static final String TAG = GlobalChain.class.getSimpleName();
    private static final String GLOBAL_OUTPUT = "GlobalOutput";

    public static void setup() {
        Logger.i(TAG, "Setup global iptables.");
        String[] cmds = new String[] {
                "iptables -w -t nat -D OUTPUT -j GlobalOutput",
                "iptables -w -t nat -F GlobalOutput",
                "iptables -w -t nat -N GlobalOutput",
                "iptables -w -t nat -I GlobalOutput -o lo -j ACCEPT",
                "iptables -w -t nat -I GlobalOutput -d 127.0.0.1 -j ACCEPT",
                "iptables -w -t nat -I OUTPUT -j GlobalOutput",
                "iptables -w -t nat -L GlobalOutput --line-numbers -nvx",

                "iptables -w -D OUTPUT -j GlobalFilter",
                "iptables -w -F GlobalFilter",
                "iptables -w -N GlobalFilter",
                "iptables -w -I GlobalFilter -p udp -j DROP",
                "iptables -w -I GlobalFilter -p udp --dport 53 -j RETURN",
                "iptables -w -A GlobalFilter -p tcp -d 127.0.0.0/8 -j RETURN",
                "iptables -w -I OUTPUT -j GlobalFilter",
                "iptables -w -L GlobalFilter --line-numbers -nvx",
        };
        Libsu.exec(String.join("; ", cmds));
    }
}
