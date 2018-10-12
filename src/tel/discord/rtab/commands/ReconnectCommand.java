package tel.discord.rtab.commands;

import tel.discord.rtab.RaceToABillionBot;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class ReconnectCommand extends Command
{
	public ReconnectCommand()
	{
		this.name = "reconnect";
		this.help = "reconnects the bot to its game channels";
		this.hidden = true;
		this.requiredRole = "Mod";
	}
	@Override
	protected void execute(CommandEvent event)
	{
		RaceToABillionBot.connectToChannels(false);
	}
}