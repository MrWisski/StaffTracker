package net.mineyourmind.mrwisski;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent event){
		StaffTracker.getInstance().playerJoin(event.getPlayer());
	}
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event){
		StaffTracker.getInstance().playerQuit(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerKick(PlayerKickEvent event){
		StaffTracker.getInstance().playerQuit(event.getPlayer());
	}

}