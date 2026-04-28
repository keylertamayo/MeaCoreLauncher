package com.experimento.launcher.service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class LanFixService {

    private LanFixService() {}

    /**
     * Devuelve la IP local de la interfaz de red activa que no sea loopback ni virtual (si es posible).
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Ignorar interfaces de loopback o que no estén activas
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Buscamos una IPv4 que no sea loopback
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            return "Unknown";
        }
        return "127.0.0.1";
    }

    /**
     * Argumentos JVM recomendados para mejorar la visibilidad LAN.
     */
    public static java.util.List<String> getNetworkFixArgs() {
        return java.util.List.of(
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.net.preferIPv4Addresses=true"
        );
    }
}
