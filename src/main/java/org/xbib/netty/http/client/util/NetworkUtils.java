/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for Java networking.
 */
public class NetworkUtils {

    private static final Logger logger = Logger.getLogger(NetworkUtils.class.getName());

    private static final String lf = System.lineSeparator();

    private static final char[] hexDigit = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static final String IPV4_SETTING = "java.net.preferIPv4Stack";

    private static final String IPV6_SETTING = "java.net.preferIPv6Addresses";

    private static InetAddress localAddress;

    public static void extendSystemProperties() {
        InetAddress address;
        try {
            address = InetAddress.getLocalHost();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            address = InetAddress.getLoopbackAddress();
        }
        localAddress = address;
        try {
            Map<String, String> map = new HashMap<>();
            map.put("net.localhost", address.getCanonicalHostName());
            String hostname = address.getHostName();
            map.put("net.hostname", hostname);
            InetAddress[] hostnameAddresses = InetAddress.getAllByName(hostname);
            int i = 0;
            for (InetAddress hostnameAddress : hostnameAddresses) {
                map.put("net.hostaddress." + (i++), hostnameAddress.getCanonicalHostName());
            }
            for (NetworkInterface networkInterface : getAllRunningAndUpInterfaces()) {
                InetAddress inetAddress = getFirstNonLoopbackAddress(networkInterface, NetworkProtocolVersion.IPV4);
                if (inetAddress != null) {
                    map.put("net." + networkInterface.getDisplayName(), inetAddress.getCanonicalHostName());
                }
            }
            logger.log(Level.FINE, "found network properties for system properties: " + map);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private NetworkUtils() {
    }

    public static boolean isPreferIPv4() {
        return Boolean.getBoolean(System.getProperty(IPV4_SETTING));
    }

    public static boolean isPreferIPv6() {
        return Boolean.getBoolean(System.getProperty(IPV6_SETTING));
    }

    public static InetAddress getIPv4Localhost() throws UnknownHostException {
        return getLocalhost(NetworkProtocolVersion.IPV4);
    }

    public static InetAddress getIPv6Localhost() throws UnknownHostException {
        return getLocalhost(NetworkProtocolVersion.IPV6);
    }

    public static InetAddress getLocalhost(NetworkProtocolVersion ipversion) throws UnknownHostException {
        return ipversion == NetworkProtocolVersion.IPV4 ?
                InetAddress.getByName("127.0.0.1") : InetAddress.getByName("::1");
    }

    public static String getLocalHostName(String defaultHostName) {
        if (localAddress == null) {
            return defaultHostName;
        }
        String hostName = localAddress.getHostName();
        if (hostName == null) {
            return defaultHostName;
        }
        return hostName;
    }

    public static String getLocalHostAddress(String defaultHostAddress) {
        if (localAddress == null) {
            return defaultHostAddress;
        }
        String hostAddress = localAddress.getHostAddress();
        if (hostAddress == null) {
            return defaultHostAddress;
        }
        return hostAddress;
    }

    public static InetAddress getLocalAddress() {
        return localAddress;
    }

    public static NetworkClass getNetworkClass(InetAddress address) {
        if (address == null || address.isAnyLocalAddress()) {
            return NetworkClass.ANY;
        }
        if (address.isLoopbackAddress()) {
            return NetworkClass.LOOPBACK;
        }
        if (address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return NetworkClass.LOCAL;
        }
        return NetworkClass.PUBLIC;
    }

    public static String format(InetAddress address) {
        return format(address, -1);
    }

    public static String format(InetSocketAddress address) {
        return format(address.getAddress(), address.getPort());
    }

    public static String format(InetAddress address, int port) {
        Objects.requireNonNull(address);
        StringBuilder sb = new StringBuilder();
        if (port != -1 && address instanceof Inet6Address) {
            sb.append(toUriString(address));
        } else {
            sb.append(toAddrString(address));
        }
        if (port != -1) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    public static String toUriString(InetAddress ip) {
        if (ip instanceof Inet6Address) {
            return "[" + toAddrString(ip) + "]";
        }
        return toAddrString(ip);
    }

    public static String toAddrString(InetAddress ip) {
        if (ip == null) {
            throw new NullPointerException("ip");
        }
        if (ip instanceof Inet4Address) {
            byte[] bytes = ip.getAddress();
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
        }
        if (!(ip instanceof Inet6Address)) {
            throw new IllegalArgumentException("ip");
        }
        byte[] bytes = ip.getAddress();
        int[] hextets = new int[8];
        for (int i = 0; i < hextets.length; i++) {
            hextets[i] = (bytes[2 * i] & 255) << 8 | bytes[2 * i + 1] & 255;
        }
        compressLongestRunOfZeroes(hextets);
        return hextetsToIPv6String(hextets);
    }

    public static boolean matchesNetwork(NetworkClass given, NetworkClass expected) {
        switch (expected) {
            case ANY:
                return EnumSet.of(NetworkClass.LOOPBACK, NetworkClass.LOCAL, NetworkClass.PUBLIC, NetworkClass.ANY)
                        .contains(given);
            case PUBLIC:
                return EnumSet.of(NetworkClass.LOOPBACK, NetworkClass.LOCAL, NetworkClass.PUBLIC)
                        .contains(given);
            case LOCAL:
                return EnumSet.of(NetworkClass.LOOPBACK, NetworkClass.LOCAL)
                        .contains(given);
            case LOOPBACK:
                return NetworkClass.LOOPBACK == given;
        }
        return false;
    }

    public static InetAddress getFirstNonLoopbackAddress(NetworkProtocolVersion ipversion) {
        InetAddress address;
        for (NetworkInterface networkInterface : getAllNetworkInterfaces()) {
            try {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
                continue;
            }
            address = getFirstNonLoopbackAddress(networkInterface, ipversion);
            if (address != null) {
                return address;
            }
        }
        return null;
    }

    public static InetAddress getFirstNonLoopbackAddress(NetworkInterface networkInterface, NetworkProtocolVersion ipVersion) {
        if (networkInterface == null) {
            throw new IllegalArgumentException("network interface is null");
        }
        for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses(); addresses.hasMoreElements(); ) {
            InetAddress address = addresses.nextElement();
            if (!address.isLoopbackAddress() && (address instanceof Inet4Address && ipVersion == NetworkProtocolVersion.IPV4) ||
                        (address instanceof Inet6Address && ipVersion == NetworkProtocolVersion.IPV6)) {
                return address;
            }
        }
        return null;
    }

    public static InetAddress getFirstAddress(NetworkInterface networkInterface, NetworkProtocolVersion ipVersion) {
        if (networkInterface == null) {
            throw new IllegalArgumentException("network interface is null");
        }
        for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses(); addresses.hasMoreElements(); ) {
            InetAddress address = addresses.nextElement();
            if ((address instanceof Inet4Address && ipVersion == NetworkProtocolVersion.IPV4) ||
                    (address instanceof Inet6Address && ipVersion == NetworkProtocolVersion.IPV6)) {
                return address;
            }
        }
        return null;
    }

    public static boolean interfaceSupports(NetworkInterface networkInterface, NetworkProtocolVersion ipVersion) {
        boolean supportsVersion = false;
        if (networkInterface != null) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if ((address instanceof Inet4Address && (ipVersion == NetworkProtocolVersion.IPV4)) ||
                        (address instanceof Inet6Address && (ipVersion == NetworkProtocolVersion.IPV6))) {
                    supportsVersion = true;
                    break;
                }
            }
        }
        return supportsVersion;
    }

    public static NetworkProtocolVersion getProtocolVersion() {
        switch (findAvailableProtocols()) {
            case IPV4:
                return NetworkProtocolVersion.IPV4;
            case IPV6:
                return NetworkProtocolVersion.IPV6;
            case IPV46:
                if (Boolean.getBoolean(System.getProperty(IPV4_SETTING))) {
                    return NetworkProtocolVersion.IPV4;
                }
                if (Boolean.getBoolean(System.getProperty(IPV6_SETTING))) {
                    return NetworkProtocolVersion.IPV6;
                }
                return NetworkProtocolVersion.IPV6;
            default:
                break;
        }
        return NetworkProtocolVersion.NONE;
    }

    public static NetworkProtocolVersion findAvailableProtocols() {
        boolean hasIPv4 = false;
        boolean hasIPv6 = false;
        for (InetAddress addr : getAllAvailableAddresses()) {
            if (addr instanceof Inet4Address) {
                hasIPv4 = true;
            }
            if (addr instanceof Inet6Address) {
                hasIPv6 = true;
            }
        }
        if (hasIPv4 && hasIPv6) {
            return NetworkProtocolVersion.IPV46;
        }
        if (hasIPv4) {
            return NetworkProtocolVersion.IPV4;
        }
        if (hasIPv6) {
            return NetworkProtocolVersion.IPV6;
        }
        return NetworkProtocolVersion.NONE;
    }

    public static InetAddress resolveInetAddress(String hostname, String defaultValue) throws IOException {
        String host = hostname;
        if (host == null) {
            host = defaultValue;
        }
        String origHost = host;
        int pos = host.indexOf(':');
        if (pos > 0) {
            host = host.substring(0, pos - 1);
        }
        if ((host.startsWith("#") && host.endsWith("#")) || (host.startsWith("_") && host.endsWith("_"))) {
            host = host.substring(1, host.length() - 1);
            if ("local".equals(host)) {
                return getLocalAddress();
            } else if (host.startsWith("non_loopback")) {
                if (host.toLowerCase(Locale.ROOT).endsWith(":ipv4")) {
                    return getFirstNonLoopbackAddress(NetworkProtocolVersion.IPV4);
                } else if (host.toLowerCase(Locale.ROOT).endsWith(":ipv6")) {
                    return getFirstNonLoopbackAddress(NetworkProtocolVersion.IPV6);
                } else {
                    return getFirstNonLoopbackAddress(getProtocolVersion());
                }
            } else {
                NetworkProtocolVersion networkProtocolVersion = getProtocolVersion();
                if (host.toLowerCase(Locale.ROOT).endsWith(":ipv4")) {
                    networkProtocolVersion = NetworkProtocolVersion.IPV4;
                    host = host.substring(0, host.length() - 5);
                } else if (host.toLowerCase(Locale.ROOT).endsWith(":ipv6")) {
                    networkProtocolVersion = NetworkProtocolVersion.IPV6;
                    host = host.substring(0, host.length() - 5);
                }
                for (NetworkInterface ni : getInterfaces(NetworkUtils::isUp)) {
                    if (host.equals(ni.getName()) || host.equals(ni.getDisplayName())) {
                        if (ni.isLoopback()) {
                            return getFirstAddress(ni, networkProtocolVersion);
                        } else {
                            return getFirstNonLoopbackAddress(ni, networkProtocolVersion);
                        }
                    }
                }
            }
            throw new IOException("failed to find network interface for [" + origHost + "]");
        }
        return InetAddress.getByName(host);
    }

    public static InetAddress resolvePublicHostAddress(String host) throws IOException {
        InetAddress address = resolveInetAddress(host, null);
        if (address == null || address.isAnyLocalAddress()) {
            address = getFirstNonLoopbackAddress(NetworkProtocolVersion.IPV4);
            if (address == null) {
                address = getFirstNonLoopbackAddress(getProtocolVersion());
                if (address == null) {
                    address = getLocalAddress();
                    if (address == null) {
                        return getLocalhost(NetworkProtocolVersion.IPV4);
                    }
                }
            }
        }
        return address;
    }

    private static List<NetworkInterface> getAllNetworkInterfaces() {
        return getInterfaces(n -> true);
    }

    public static List<NetworkInterface> getAllRunningAndUpInterfaces() {
        return getInterfaces(NetworkUtils::isUp);
    }

    public static List<NetworkInterface> getInterfaces(Predicate<NetworkInterface> predicate) {
        List<NetworkInterface> networkInterfaces = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (Exception e) {
            return networkInterfaces;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (predicate.test(networkInterface)) {
                networkInterfaces.add(networkInterface);
                Enumeration<NetworkInterface> subInterfaces = networkInterface.getSubInterfaces();
                if (subInterfaces.hasMoreElements()) {
                    while (subInterfaces.hasMoreElements()) {
                        networkInterfaces.add(subInterfaces.nextElement());
                    }
                }
            }
        }
        sortInterfaces(networkInterfaces);
        return networkInterfaces;
    }

    public static List<InetAddress> getAllAvailableAddresses() {
        List<InetAddress> allAddresses = new ArrayList<>();
        for (NetworkInterface networkInterface : getAllNetworkInterfaces()) {
            Enumeration<InetAddress> addrs = networkInterface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                allAddresses.add(addrs.nextElement());
            }
        }
        sortAddresses(allAddresses);
        return allAddresses;
    }

    public static String displayNetworkInterfaces() {
        StringBuilder sb = new StringBuilder();
        for (NetworkInterface nic : getAllNetworkInterfaces()) {
            sb.append(displayNetworkInterface(nic));
        }
        return sb.toString();
    }

    public static String displayNetworkInterface(NetworkInterface nic) {
        StringBuilder sb = new StringBuilder();
        sb.append(lf).append(nic.getName()).append(lf);
        if (!nic.getName().equals(nic.getDisplayName())) {
            sb.append("\t").append(nic.getDisplayName()).append(lf);
        }
        sb.append("\t").append("flags ");
        List<String> flags = new ArrayList<>();
        try {
            if (nic.isUp()) {
                flags.add("UP");
            }
            if (nic.supportsMulticast()) {
                flags.add("MULTICAST");
            }
            if (nic.isLoopback()) {
                flags.add("LOOPBACK");
            }
            if (nic.isPointToPoint()) {
                flags.add("POINTTOPOINT");
            }
            if (nic.isVirtual()) {
                flags.add("VIRTUAL");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        sb.append(String.join(",", flags));
        try {
            sb.append(" mtu ").append(nic.getMTU()).append(lf);
        } catch (SocketException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        List<InterfaceAddress> addresses = nic.getInterfaceAddresses();
        for (InterfaceAddress address : addresses) {
            sb.append("\t").append(formatAddress(address)).append(lf);
        }
        try {
            byte[] b = nic.getHardwareAddress();
            if (b != null) {
                sb.append("\t").append("ether ");
                for (int i = 0; i < b.length; i++) {
                    if (i > 0) {
                        sb.append(":");
                    }
                    sb.append(hexDigit[(b[i] >> 4) & 0x0f]).append(hexDigit[b[i] & 0x0f]);
                }
                sb.append(lf);
            }
        } catch (SocketException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return sb.toString();
    }

    private static void sortInterfaces(List<NetworkInterface> interfaces) {
        interfaces.sort(Comparator.comparingInt(NetworkInterface::getIndex));
    }

    private static void sortAddresses(List<InetAddress> addressList) {
        addressList.sort((o1, o2) -> compareBytes(o1.getAddress(), o2.getAddress()));
    }

    private static String formatAddress(InterfaceAddress interfaceAddress) {
        StringBuilder sb = new StringBuilder();
        InetAddress address = interfaceAddress.getAddress();
        if (address instanceof Inet6Address) {
            sb.append("inet6 ").append(format(address))
                    .append(" prefixlen:").append(interfaceAddress.getNetworkPrefixLength());
        } else {
            int netmask = 0xFFFFFFFF << (32 - interfaceAddress.getNetworkPrefixLength());
            byte[] b = new byte[] {
                    (byte) (netmask >>> 24),
                    (byte) (netmask >>> 16 & 0xFF),
                    (byte) (netmask >>> 8 & 0xFF),
                    (byte) (netmask & 0xFF)
            };
            sb.append("inet ").append(format(address));
            try {
                sb.append(" netmask:").append(format(InetAddress.getByAddress(b)));
            } catch (UnknownHostException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            InetAddress broadcast = interfaceAddress.getBroadcast();
            if (broadcast != null) {
                sb.append(" broadcast:").append(format(broadcast));
            }
        }
        if (address.isLoopbackAddress()) {
            sb.append(" scope:host");
        } else if (address.isLinkLocalAddress()) {
            sb.append(" scope:link");
        } else if (address.isSiteLocalAddress()) {
            sb.append(" scope:site");
        }
        return sb.toString();
    }

    private static boolean isUp(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp();
        } catch (SocketException e) {
            return false;
        }
    }

    private static int compareBytes(byte[] left, byte[] right) {
        for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
            int a = left[i] & 0xff;
            int b = right[j] & 0xff;
            if (a != b) {
                return a - b;
            }
        }
        return left.length - right.length;
    }

    private static void compressLongestRunOfZeroes(int[] hextets) {
        int bestRunStart = -1;
        int bestRunLength = -1;
        int runStart = -1;
        for (int i = 0; i < hextets.length + 1; i++) {
            if (i < hextets.length && hextets[i] == 0) {
                if (runStart < 0) {
                    runStart = i;
                }
            } else if (runStart >= 0) {
                int runLength = i - runStart;
                if (runLength > bestRunLength) {
                    bestRunStart = runStart;
                    bestRunLength = runLength;
                }
                runStart = -1;
            }
        }
        if (bestRunLength >= 2) {
            Arrays.fill(hextets, bestRunStart, bestRunStart + bestRunLength, -1);
        }
    }

    private static String hextetsToIPv6String(int[] hextets) {
        StringBuilder sb = new StringBuilder(39);
        boolean lastWasNumber = false;
        for (int i = 0; i < hextets.length; i++) {
            boolean b = hextets[i] >= 0;
            if (b) {
                if (lastWasNumber) {
                    sb.append(':');
                }
                sb.append(Integer.toHexString(hextets[i]));
            } else {
                if (i == 0 || lastWasNumber) {
                    sb.append("::");
                }
            }
            lastWasNumber = b;
        }
        return sb.toString();
    }
}
