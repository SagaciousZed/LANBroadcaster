package eu.icecraft_mc.LANBroadcaster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class LANBroadcaster extends JavaPlugin {
    final static String payload = "[MOTD]%s:%s[/MOTD][AD]%s[/AD]";
    final static String multicastAddress = "224.0.2.60";
    final static int PORT = 4445;

    private InetAddress group;
    private MulticastSocket socket;

    @Override
    public void onEnable() {
        try {
            this.group = InetAddress.getByName(multicastAddress);
            this.socket = new MulticastSocket(this.PORT);

            this.socket.setTimeToLive(3);
            this.socket.joinGroup(this.group);

            final String actualIp = Bukkit.getIp().length() == 0 ? InetAddress.getLocalHost().getHostAddress() : Bukkit.getIp();
            final String formattedPayload = String.format(this.payload, Bukkit.getMotd(), Bukkit.getPort(), actualIp);
            this.getLogger().info("Multicast packet payload: " + formattedPayload);

            final byte[] b = formattedPayload.getBytes();
            final DatagramPacket d = new DatagramPacket(b, b.length, this.group, this.PORT);

            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, new Runnable() {
                AtomicBoolean running = new AtomicBoolean();

                public void run() {
                    try {
                        if (running.compareAndSet(false, true)) {
                            LANBroadcaster.this.socket.send(d);
                            running.set(false);
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException(LANBroadcaster.this.toString() + " failed to send broadcast packet", e);
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
        this.socket.close();
    }
}
