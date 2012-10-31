package eu.icecraft_mc.LANBroadcaster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LANBroadcaster extends JavaPlugin {
    final static String MULTICAST_ADDRESS = "224.0.2.60";
    final static String PAYLOAD = "[MOTD]%s[/MOTD][AD]%s:%s[/AD]";
    final static int PORT = 4445;

    private final List<MulticastSocket> boundSockets = new ArrayList<MulticastSocket>();;

    private InetAddress group;

    @Override
    public void onEnable() {
        try {
            this.group = InetAddress.getByName(LANBroadcaster.MULTICAST_ADDRESS);

            this.bindSockets();

            // Create packets for each device
            final List<DatagramPacket> packets = new ArrayList<DatagramPacket>();
            for (final MulticastSocket ms : this.boundSockets) {
                final DatagramPacket datagramPacket = this.createDatagramPacket(ms.getLocalAddress());
                packets.add(datagramPacket);
            }

            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, new Runnable() {
                final AtomicBoolean running = new AtomicBoolean();

                public void run() {
                    if (this.running.compareAndSet(false, true)) {
                        try {
                            for (int i = 0; i < LANBroadcaster.this.boundSockets.size(); i++) {
                                LANBroadcaster.this.boundSockets.get(i).send(packets.get(i));
                            }
                            this.running.set(false);

                        } catch (final IOException e) {
                            throw new RuntimeException(LANBroadcaster.this.toString() + " failed to send broadcast packet", e);
                        } finally {
                            this.running.set(false);
                        }
                    }
                }
            }, 0, 30l);
        } catch (final IOException e) {
            this.getLogger().log(Level.SEVERE, e.getMessage(), e);
            this.getPluginLoader().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        for (final MulticastSocket ms : this.boundSockets) {
            ms.close();
        }
    }

    private DatagramPacket createDatagramPacket(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException();
        }
        final String s = String.format(LANBroadcaster.PAYLOAD, Bukkit.getMotd(), address.getHostAddress(), Bukkit.getPort());
        this.getLogger().info("Multicast packet payload: " + s);
        final byte[] b = s.getBytes();
        return new DatagramPacket(b, b.length, this.group, LANBroadcaster.PORT);
    }

    /**
     * 
     * @return List<NetworkInterface> excluding loop back interfaces
     * @throws SocketException
     */
    private static List<NetworkInterface> discoverNetworkInterfaces() throws SocketException {
        final List<NetworkInterface> list = new ArrayList<NetworkInterface>();
        final Enumeration<NetworkInterface> nicEnum = NetworkInterface.getNetworkInterfaces();
        while (nicEnum.hasMoreElements()) {
            final NetworkInterface nic = nicEnum.nextElement();
            if (nic.isLoopback()) {
                continue;
            }
            list.add(nic);
        }
        return list;
    }

    private static List<InetAddress> discoverAddresses(NetworkInterface nic, boolean onlyIpv4) {
        final List<InetAddress> list = new ArrayList<InetAddress>();

        final Enumeration<InetAddress> addressEnum = nic.getInetAddresses();
        while (addressEnum.hasMoreElements()) {
            final InetAddress address = addressEnum.nextElement();
            if (onlyIpv4 && address instanceof Inet6Address) {
                continue;
            }
            list.add(address);
        }
        return list;
    }

    private void bindSockets() throws IOException {
        // bind sockets to each network device
        for (final NetworkInterface nic : LANBroadcaster.discoverNetworkInterfaces()) {
            for (final InetAddress inetAddress : LANBroadcaster.discoverAddresses(nic, true)) {
                this.getLogger().info("Binding to " + inetAddress.getHostAddress());
                final MulticastSocket ms = new MulticastSocket(new InetSocketAddress(inetAddress, LANBroadcaster.PORT));
                ms.setSoTimeout(1);
                ms.joinGroup(this.group);
                ms.setBroadcast(true);
                if (!ms.isBound()) {
                    throw new IllegalStateException();
                }
                this.boundSockets.add(ms);
            }
        }
    }
}
