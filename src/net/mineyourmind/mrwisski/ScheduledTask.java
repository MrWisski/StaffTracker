package net.mineyourmind.mrwisski;


import org.bukkit.scheduler.BukkitRunnable;
 
public class ScheduledTask extends BukkitRunnable {
 
    private StaffTracker plugin;
 
    public ScheduledTask(StaffTracker plugin) {
        this.plugin = plugin;
    }
 
    @Override
    public void run() {
        // What you want to schedule goes here
        plugin.updateAllRecords();
    }
 
}