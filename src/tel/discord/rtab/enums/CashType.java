package tel.discord.rtab.enums;

import net.dv8tion.jda.core.utils.tuple.Pair;

public enum CashType implements WeightedSpace
{
	//Negative
	N25K	(-  25000,3),
	N20K	(-  20000,3),
	N15K	(-  15000,3),
	N10K	(-  10000,3),
	N05K	(-   5000,3),
	//TODO - FIX DEBUG VALUES
	//Small
	P10K	(   10000,5),
	P20K	(   20000,5),
	P30K	(   30000,5),
	P40K	(   40000,5),
	P50K	(   50000,5),
	P60K	(   60000,5),
	P70K	(   70000,5),
	P80K	(   80000,5),
	P90K	(   90000,5),
	P100K	(  100000,5),
	//Big
	P111K	(  111111,2),
	P125K	(  125000,2),
	P150K	(  150000,2),
	P200K	(  200000,2),
	P250K	(  250000,2),
	P300K	(  300000,2),
	P400K	(  400000,2),
	P500K	(  500000,2),
	P750K	(  750000,2),
	P1000K	( 1000000,2),
	//Other
	P10		(      10,1),
	MYSTERY (       0,3),
	PRIZE   (       0,3)
	{
		@Override
		public Pair<Integer,String> getValue()
		{
			PrizeType[] prizes = PrizeType.values();
			PrizeType prize = prizes[(int) (Math.random() * (prizes.length - 1) + 1)];
			Pair<Integer,String> data = Pair.of(prize.getPrizeValue(), prize.getPrizeName());
			return data;
		}
	};
	
	int value;
	int weight;
	CashType(int cashValue, int valueWeight)
	{
		value = cashValue;
		weight = valueWeight;
	}
	@Override
	public int getWeight(int playerCount)
	{
		//Cash types don't care about playercount
		return weight;
	}
	public Pair<Integer,String> getValue()
	{
		Pair<Integer,String> data = Pair.of(value, null);
		return data;
	}
}
