package tel.discord.rtab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import tel.discord.rtab.enums.GameStatus;
import tel.discord.rtab.enums.PlayerJoinReturnValue;
import tel.discord.rtab.enums.PlayerQuitReturnValue;
import tel.discord.rtab.enums.PlayerStatus;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;

public class GameController
{
	static int boardSize = 15;
	public static MessageChannel channel = null;
	static List<Player> players = new ArrayList<>();
	static int currentTurn = -1;
	public static int playersJoined = 0;
	static int playersAlive = 0;
	public static GameStatus gameStatus = GameStatus.SIGNUPS_OPEN;
	static boolean[] pickedSpaces;
	static int spacesLeft;
	static boolean[] bombs;
	static Board gameboard;
	public static EventWaiter waiter;
	/*
	 * reset - (re)initialises the game state by removing all players and clearing the board.
	 */
	public static void reset()
	{
		channel = null;
		players.clear();
		currentTurn = -1;
		playersJoined = 0;
		playersAlive = 0;
		gameStatus = GameStatus.SIGNUPS_OPEN;
		gameboard = null;
	}
	/*
	 * addPlayer - adds a player to the game, or updates their name if they're already in.
	 * MessageChannel channelID - channel the request took place in (only used to know where to send game details to)
	 * String playerID - ID of player to be added.
	 * Returns an enum which gives the result of the join attempt.
	 */
	public static PlayerJoinReturnValue addPlayer(MessageChannel channelID, Member playerID)
	{
		//Make sure game isn't already running
		if(gameStatus != GameStatus.SIGNUPS_OPEN)
			return PlayerJoinReturnValue.INPROGRESS;
		//Are they in the right channel?
		if(playersJoined == 0)
			channel = channelID;
		else if(channel != channelID)
			return PlayerJoinReturnValue.WRONGCHANNEL;
		//Create player object
		Player newPlayer = new Player(playerID);
		//Look for match already in player list
		for(int i=0; i<playersJoined; i++)
		{
			if(players.get(i).uID.equals(newPlayer.uID))
			{
				//Found them, check if we should update their name or just laugh at them
				if(players.get(i).name == newPlayer.name)
					return PlayerJoinReturnValue.ALREADYIN;
				else
				{
					players.set(i,newPlayer);
					return PlayerJoinReturnValue.UPDATED;
				}
			}
		}
		//Haven't found one, add them to the list
		players.add(newPlayer);
		playersJoined++;
		return PlayerJoinReturnValue.JOINED;
	}
	/*
	 * removePlayer - removes a player from the game.
	 * MessageChannel channelID - channel the request was registered in.
	 * String playerID - ID of player to be removed.
	 */
	public static PlayerQuitReturnValue removePlayer(MessageChannel channelID, User playerID)
	{
		//Make sure game isn't running, too late to quit now
		if(gameStatus != GameStatus.SIGNUPS_OPEN)
			return PlayerQuitReturnValue.GAMEINPROGRESS;
		//Search for player
		for(int i=0; i<playersJoined; i++)
		{
			if(players.get(i).uID.equals(playerID.getId()))
			{
				//Found them, get rid of them and call it a success
				players.remove(i);
				return PlayerQuitReturnValue.SUCCESS;
			}
		}
		//Didn't find them, why are they trying to quit in the first place?
		return PlayerQuitReturnValue.NOTINGAME;
	}
	/*
	 * runGame - controls the actual game logic once the game is ready to go.
	 */
	public static void startTheGameAlready()
	{
		//Declare game in progress so we don't get latecomers
		gameStatus = GameStatus.IN_PROGRESS;
		//Initialise stuff that needs initialising
		boardSize = 5 + (5*playersJoined);
		spacesLeft = boardSize;
		pickedSpaces = new boolean[boardSize];
		bombs = new boolean[boardSize];
		//Request players send in bombs, and set up waiter for them to return
		for(int i=0; i<playersJoined; i++)
		{
			final int iInner = i;
			players.get(iInner).user.openPrivateChannel().queue(
					(channel) -> channel.sendMessage("Please PM your bomb by sending a number 1-" + boardSize).queue());
			waiter.waitForEvent(MessageReceivedEvent.class,
					//Check if right player, and valid bomb pick
					e -> (e.getAuthor().equals(players.get(iInner).user) && checkValidNumber(e.getMessage().getContentRaw())),
					//Parse it and update the bomb board
					e -> 
					{
						bombs[Integer.parseInt(e.getMessage().getContentRaw())-1] = true;
						players.get(iInner).user.openPrivateChannel().queue(
								(channel) -> channel.sendMessage("Bomb placement confirmed.").queue());
						players.get(iInner).status = PlayerStatus.ALIVE;
						playersAlive ++;
						checkReady();
					},
					//Or timeout after a minute
					1, TimeUnit.MINUTES, () ->
					{
						gameStatus = GameStatus.SIGNUPS_OPEN;
						checkReady();
					});
		}

	}
	static void checkReady()
	{
		if(gameStatus == GameStatus.SIGNUPS_OPEN)
		{
			channel.sendMessage("Bomb placement timed out. Game aborted.").queue();
		}
		else
		{
			//If everyone has sent in, what are we waiting for?
			if(playersAlive == playersJoined)
			{
				//Determine first player
				currentTurn = (int)(Math.random()*playersJoined);
				gameboard = new Board(boardSize);
				channel.sendMessage("Let's go!").queue();
				runTurn();
			}
		}
	}
	static void runTurn()
	{
		channel.sendMessage(players.get(currentTurn).user.getAsMention() + ", your turn. Choose a space on the board.")
			.completeAfter(3,TimeUnit.SECONDS);
		displayBoardAndStatus();
		waiter.waitForEvent(MessageReceivedEvent.class,
				//Right player and channel
				e ->
				{
					if(e.getAuthor().equals(players.get(currentTurn).user) && e.getChannel().equals(channel)
							&& checkValidNumber(e.getMessage().getContentRaw()))
					{
							int location = Integer.parseInt(e.getMessage().getContentRaw());
							if(pickedSpaces[location-1])
							{
								channel.sendMessage("That space has already been picked.").queue();
								return false;
							}
							else
								return true;
					}
					return false;
				},
				//Parse it, update the board, and reveal the result
				e -> 
				{
					int location = Integer.parseInt(e.getMessage().getContentRaw())-1;
					pickedSpaces[location] = true;
					spacesLeft--;
					channel.sendMessage("Space " + (location+1) + " selected...").completeAfter(1,TimeUnit.SECONDS);
					if(bombs[location])
					{
						if(Math.random()<0.5)
							channel.sendMessage("...").completeAfter(5,TimeUnit.SECONDS);
						channel.sendMessage("**BOOM**").completeAfter(5,TimeUnit.SECONDS);
						channel.sendMessage(players.get(currentTurn).user.getAsMention() +
								" loses $250,000 as penalty for blowing up.").queue();
						StringBuilder extraResult = null;
						extraResult = players.get(currentTurn).addMoney(-250000,false);
						if(extraResult != null)
							channel.sendMessage(extraResult).queue();
						players.get(currentTurn).status = PlayerStatus.OUT;
						players.get(currentTurn).booster = 100;
						players.get(currentTurn).winstreak = 0;
						playersAlive --;
					}
					else
					{
						if((Math.random()*spacesLeft)<1)
							channel.sendMessage("...").completeAfter(5,TimeUnit.SECONDS);
						//Figure out what space we got
						StringBuilder resultString = new StringBuilder();
						StringBuilder extraResult = null;
						switch(gameboard.typeBoard[location])
						{
						case CASH:
							//On cash, update the player's score and tell them how much they won
							int cashWon = gameboard.cashBoard[location];
							resultString.append("**");
							if(cashWon<0)
								resultString.append("-");
							resultString.append("$");
							resultString.append(String.format("%,d",Math.abs(cashWon)));
							resultString.append("**");
							extraResult = players.get(currentTurn).addMoney(cashWon,false);
							break;
						case BOOSTER:
							//On cash, update the player's booster and tell them what they found
							int boostFound = gameboard.boostBoard[location];
							resultString.append("A **" + String.format("%+d",boostFound) + "%** Booster!");
							players.get(currentTurn).addBooster(boostFound);
							break;
						}
						channel.sendMessage(resultString).completeAfter(5,TimeUnit.SECONDS);
						if(extraResult != null)
							channel.sendMessage(extraResult).queue();
					}
					//Advance turn to next player
					advanceTurn();
					//Test if game over
					if(spacesLeft == 0 || playersAlive == 1)
					{
						gameStatus = GameStatus.END_GAME;
						channel.sendMessage("Game Over.").completeAfter(3,TimeUnit.SECONDS);
						for(int i=0; i<playersAlive; i++)
						{
							channel.sendMessage(players.get(currentTurn).user.getAsMention() + " Wins!")
								.completeAfter(1,TimeUnit.SECONDS);
							//Boost winstreak by number of opponents defeated
							players.get(currentTurn).winstreak += (playersJoined - playersAlive);
							//But it always gets to be at least 1
							if(players.get(currentTurn).winstreak == 0)
								players.get(currentTurn).winstreak = 1;
							//Award $20k for each space picked, double it if every space was picked, then share with everyone in
							int winBonus = 20000*(boardSize-spacesLeft);
							if(spacesLeft == 0)
								winBonus *= 2;
							winBonus /= playersAlive;
							channel.sendMessage(players.get(currentTurn).name + " receives a win bonus of **$"
									+ String.format("%,d",winBonus) + "**.").queue();
							StringBuilder extraResult = null;
							extraResult = players.get(currentTurn).addMoney(winBonus,true);
							if(extraResult != null)
								channel.sendMessage(extraResult).queue();
							advanceTurn();
						}
						displayBoardAndStatus();
						saveData();
						reset();
					}
					else
					{
						runTurn();
					}
				});
	}
	static void advanceTurn()
	{
		//Keep spinning through until we've got someone who's still in the game
		do
		{
			currentTurn++;
			currentTurn = currentTurn % playersJoined;
		}
		while(players.get(currentTurn).status == PlayerStatus.OUT);
	}
	static boolean checkValidNumber(String message)
	{
		try
		{
			int location = Integer.parseInt(message);
			return (location > 0 && location <= boardSize);
		}
		catch(NumberFormatException e1)
		{
			return false;
		}
	}
	static void displayBoardAndStatus()
	{
		//Build up board display
		StringBuilder board = new StringBuilder().append("```\n");
		board.append("     RtaB     \n");
		for(int i=0; i<boardSize; i++)
		{
			if(pickedSpaces[i])
			{
				board.append("  ");
			}
			else
			{
				board.append(String.format("%02d",(i+1)));
			}
			if(i%5==4)
				board.append("\n");
			else
				board.append(" ");
		}
		board.append("\n");
		//Next the status line
		//Start by getting the lengths so we can pad the status bars appropriately
		//Add one extra to name length because we want one extra space between name and cash
		int nameLength = players.get(0).name.length();
		for(int i=1; i<playersJoined; i++)
			nameLength = Math.max(nameLength,players.get(i).name.length());
		nameLength ++;
		//And ignore the negative sign if there is one
		int moneyLength = String.valueOf(Math.abs(players.get(0).money)).length();
		for(int i=1; i<playersJoined; i++)
			moneyLength = Math.max(moneyLength, String.valueOf(Math.abs(players.get(i).money)).length());
		//Do we need to worry about negatives?
		boolean negativeExists = false;
		for(int i=0; i<playersJoined;i++)
			if(players.get(i).money<0)
			{
				negativeExists = true;
				break;
			}
		//Make a little extra room for the commas
		moneyLength += (moneyLength-1)/3;
		//Then start printing - including pointer if currently their turn
		for(int i=0; i<playersJoined; i++)
		{
			if(currentTurn == i)
				board.append("> ");
			else
				board.append("  ");
			board.append(String.format("%-"+nameLength+"s",players.get(i).name));
			//Now figure out if we need a negative sign, a space, or neither
			if(players.get(i).money<0)
				board.append("-");
			else if(negativeExists)
				board.append(" ");
			//Then print the money itself
			board.append("$");
			board.append(String.format("%,"+moneyLength+"d",Math.abs(players.get(i).money)));
			//Now the booster display
			if(players.get(i).status == PlayerStatus.ALIVE)
			{
				board.append(" [");
				board.append(String.format("%03d",players.get(i).booster));
				board.append("%");
				if(gameStatus == GameStatus.END_GAME)
				{
					board.append("x");
					board.append(players.get(i).winstreak);
				}
			}
			else
				board.append(" [OUT ");
			board.append("]\n");
		}
		//Close it off and print it out
		board.append("```");
		channel.sendMessage(board.toString()).queue();
	}
	static void saveData()
	{
		try
		{
			List<String> list = Files.readAllLines(Paths.get("scores.csv"));
			//Replace the records of the players if they're there, otherwise add them
			for(int i=0; i<playersJoined; i++)
			{
				int location = findUserInList(list,players.get(i).uID,false);
				String toPrint = players.get(i).uID+":"+players.get(i).name+":"+players.get(i).money
						+":"+players.get(i).booster+":"+players.get(i).winstreak;
				if(location == -1)
					list.add(toPrint);
				else
					list.set(location,toPrint);
			}
			//Then sort and rewrite it
			DescendingScoreSorter sorter = new DescendingScoreSorter();
			list.sort(sorter);
			Path file = Paths.get("scores.csv");
			Path fileOld = Paths.get("scoresOld.csv");
			Files.delete(fileOld);
			Files.copy(file,fileOld);
			Files.delete(file);
			Files.write(file, list);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	public static int findUserInList(List<String> list, String userID, boolean searchByName)
	{
		int field;
		if(searchByName)
			field = 1;
		else
			field = 0;
		/*
		 * record format:
		 * record[0] = uID
		 * record[1] = name
		 * record[2] = money
		 * record[3] = booster
		 * record[4] = winstreak
		 */
		String[] record;
		for(int i=0; i<list.size(); i++)
		{
			record = list.get(i).split(":");
			if(record[field].equals(userID))
				return i;
		}
		return -1;
	}
}
