package net.mineyourmind.mrwisski;

import java.util.ArrayList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class EventListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event){
		String Command = "";
		ArrayList<String> ArgList = new ArrayList<String>();
		String label = "";
		
		String preproc[] = event.getMessage().split(" ");
		StaffTracker.Log.info("DEBUG : PCPE length is " + preproc.length);
		
		int count = 0;
		for(String s : preproc){
			StaffTracker.Log.info("DEBUG : preproc[" + count + "] == " + s);
			if(count == 0){
				label = s;
				Command = s.substring(1, s.length());
				StaffTracker.Log.info("DEBUG : Command = " + Command + " -- Label == " + label);
			} else {
				ArgList.add(s);
				StaffTracker.Log.info("DEBUG : added argument : " + s);
			}
			count++;
		}
		
		StaffTracker.getInstance().onOtherCommand(Command,event.getPlayer(), ArgList);
		
		//StaffTracker.getInstance().playerJoin(event.getPlayer());
	}
	
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