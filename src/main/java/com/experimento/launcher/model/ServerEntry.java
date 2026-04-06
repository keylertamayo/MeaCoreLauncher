package com.experimento.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerEntry {
    public String name = "";
    public String address = "";
    /** User-declared: server allows non-premium (Aternos "Cracked"). */
    public boolean crackedServer = true;
    public boolean moddedHint = false;
    /** Optional hint shown in UI; does not auto-switch client version. */
    public String serverVersionHint = "";

    public ServerEntry() {}

    public ServerEntry(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
