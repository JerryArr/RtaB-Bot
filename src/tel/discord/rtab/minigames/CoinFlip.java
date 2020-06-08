package tel.discord.rtab.minigames;

import java.util.LinkedList;

public class CoinFlip implements MiniGame {
	static final String NAME = "CoinFlip";
	static final boolean BONUS = false; 
	static final int[] PAYTABLE = {5_000,10_000,25_000,50_000,100_000,250_000,500_000,1_000_000};
	static final int MAX_STAGE = PAYTABLE.length-1;
	int baseMultiplier;
	int stage;
	int coins;
	boolean alive; //Player still alive?
	boolean accept; //Accepting the Offer
	/**
	 * Initialises the variables used in the minigame and prints the starting messages.
	 * @return A list of messages to send to the player.
	 */
	@Override
	public LinkedList<String> initialiseGame(String channelID, int baseMultiplier){
		this.baseMultiplier = baseMultiplier;
		stage = 0; // We always start on Stage 0
		coins = 10;
		
		alive = true; 
		accept = false;

		LinkedList<String> output = new LinkedList<>();
		//Give instructions
		output.add("Welcome to CoinFlip!");
		output.add("Here there are "+MAX_STAGE+" stages to clear, "
				+ "and up to "+String.format("**$%,d**", payTable(MAX_STAGE))+" to be won!");
		output.add("You start with ten coins, and at each stage you choose Heads or Tails.");
		output.add("As long as even one coin shows your choice, you clear the stage.");
		output.add("However, any coins that land on the wrong side are removed from your collection.");
		output.add("You can stop at any time, but if you ever run out of coins you will lose 90% of your bank."); //~NOT DUH?!?!
		output.add(ShowPaytable(stage));
		output.add(makeOverview(coins, stage)); 
		return output;  
	}

	/**
	 * Takes the next player input and uses it to play the next "turn" - up until the next input is required.
	 * @param pick The next input sent by the player.
	 * @return A list of messages to send to the player.
	 */
	@Override
	public LinkedList<String> playNextTurn(String pick){
		LinkedList<String> output = new LinkedList<>();

		boolean heads = false; //Default variable
		boolean tails = false; //Default variable

		String choice = pick.toUpperCase();
		choice = choice.replaceAll("\\s","");
		if(choice.equals("HEADS") || choice.equals("H"))
		{
			heads = true;
		}
		else if (choice.equals("TAILS") || choice.equals("T"))
		{
			tails = true;
		}
		else if(choice.equals("ACCEPT") || choice.equals("DEAL") || choice.equals("TAKE") || choice.equals("STOP") || choice.equals("S"))
		{
			accept = true;
			output.add("You took the money!");
		}
		else if(choice.equals("!PAYTABLE"))
		{
			output.add(ShowPaytable(stage));
			output.add(makeOverview(coins, stage));
		}
		//If it's none of those it's just some random string we can safely ignore
		
		if(heads || tails)
		{	
			int newCoins = 0;
			for(int i=0; i < coins; i++)
			{
				if (0.5 < Math.random()){
					if (tails) newCoins++;
				}
				else
				{
					if (heads) newCoins++;
				}
			}
			output.add(String.format("Flipping %d coin"+(coins!=1?"s":"")+"...", coins));
			if (heads)
				output.add(String.format("You got %d HEADS"+(newCoins==0?".":(coins/newCoins>=2?".":"!")), newCoins));
			else if (tails)
				output.add(String.format("You got %d TAILS"+(newCoins==0?".":(coins/newCoins>=2?".":"!")), newCoins));
			coins = newCoins;
			stage++;
			if (coins == 0)
			{
				alive = false;
			}
			else
			{
				output.add(String.format("You cleared Stage %d and won $%,d! \n", stage, payTable(stage)));
				if (stage >= MAX_STAGE) accept = true;
				else output.add(makeOverview(coins, stage));
			}
		}
		
		return output;
	}

	/**
	* @param  stage Shows the selected Stage bold.
	* @return Will Return a nice looking Paytable with all Infos
	**/
	private String ShowPaytable(int stage)
	{
		StringBuilder output = new StringBuilder();
		output.append("```\n");
		output.append("     Win Stages    \n\n");
		for(int i=0; i<=MAX_STAGE; i++)
			output.append(String.format("Stage %1$d: $%2$,9d\n", i, payTable(i)));
		output.append("```");
		return output.toString();
	}

	/**
	* @param coins The amount of coins left
	* @param stage The current stage
	* @return Will Return a nice looking output with all Infos
	**/
	private String makeOverview(int coins, int stage)
	{
		StringBuilder output = new StringBuilder();
		output.append("```\n");
		output.append("  CoinFlip  \n\n");
		output.append("Current Coins: " + String.format("%d \n", coins));
		output.append("Current Stage: " + String.format("%d - ", stage) + String.format("$%,d\n", payTable(stage)));
		output.append("   Next Stage: " + String.format("%d - ", stage+1) + String.format("$%,d\n", payTable(stage+1)));
		output.append("Current Bailout:   " + String.format("$%,d\n\n",payTable(stage+1)/10));
		output.append("'Heads' or 'Tails'   (or 'Stop')? \n");
		output.append("```");
		return output.toString();
	}

	private int payTable(int stage)
	{
		//If it's a stage on the paytable, return that
		if(stage >= 0 && stage <= MAX_STAGE)
			return(PAYTABLE[stage]*baseMultiplier);
		else
			return 0;
	}

	/**
	 * Returns true if the minigame has ended
	 */
	@Override
	public boolean isGameOver(){
		return accept || !alive;
	}


	/**
	 * Returns an int containing the player's winnings, pre-booster.
	 * If game isn't over yet, should return lowest possible win (usually 0) because player timed out for inactivity.
	 */
	@Override
	public int getMoneyWon()
	{
		return (alive) ? payTable(stage) : payTable(stage) / 10;
	}
	/**
	 * Returns true if the game is a bonus game (and therefore shouldn't have boosters or winstreak applied)
	 * Returns false if it isn't (and therefore should have boosters and winstreak applied)
	 */
	@Override
	public boolean isBonusGame()
	{
		return BONUS;
	}
	
	@Override
	public String getBotPick()
	{
		//Do a "trial run" and quit if it fails
		if (Math.random()*Math.pow(2,coins) > 1)
		{
			// Decide heads or tails randomly
			if (0.5 < Math.random()){
					return "TAILS";
				}
				else{
					return "HEADS";
				}
		}
		return "STOP";
	}
	
	@Override
	public String toString()
	{
		return NAME;
	}
}