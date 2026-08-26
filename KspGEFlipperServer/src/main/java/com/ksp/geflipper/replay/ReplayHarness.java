package com.ksp.geflipper.replay;

import com.ksp.geflipper.executionmodel.GeTaxService;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Offline fill-aware replay utility. CSV: timestamp,itemId,high,low,highVolume,lowVolume. */
public final class ReplayHarness {
    private ReplayHarness() {}
    public static void main(String[] args)throws Exception{if(args.length<1){System.out.println("Usage: ReplayHarness market.csv [startingGp]");return;}long gp=args.length>1?Long.parseLong(args[1]):10_000_000L;List<Row> rows=read(Path.of(args[0]));Result r=run(rows,gp);System.out.printf(Locale.ROOT,"trades=%d realizedGp=%d gpPerHour=%.0f winRate=%.2f maxDrawdown=%d capitalUtilization=%.2f slotUtilization=%.2f%n",r.trades,r.realizedGp,r.gpPerHour,r.winRate,r.maxDrawdown,r.capitalUtilization,r.slotUtilization);}
    static Result run(List<Row> rows,long startingGp){if(rows.isEmpty())return new Result(0,0,0,0,0,0,0);GeTaxService tax=new GeTaxService();long cash=startingGp,peak=startingGp,maxDd=0,profit=0;int trades=0,wins=0;double capitalMinutes=0,slotMinutes=0;Map<Integer,Row> prev=new HashMap<>();long start=rows.get(0).timestamp,end=rows.get(rows.size()-1).timestamp;for(Row r:rows){Row p=prev.put(r.itemId,r);if(p==null||p.high<=p.low||r.high<=r.low)continue;long spread=r.high-r.low;long buy=r.low+Math.max(1,spread/4),sell=r.high-Math.max(1,spread/4);long unit=tax.postTaxUnitPrice("",sell)-buy;if(unit<=0)continue;long flow=Math.max(1,Math.min(r.highVolume,r.lowVolume));int qty=(int)Math.min(1000,Math.min(flow/20,Math.max(0,cash/Math.max(1,buy)/10)));if(qty<=0)continue;double buyFill=fillProbability(buy,r.low,r.high,qty,flow),sellFill=fillProbability(sell,r.low,r.high,qty,flow);if(buyFill*sellFill<0.30)continue;long pnl=unit*qty;profit+=pnl;cash+=pnl;trades++;if(pnl>0)wins++;capitalMinutes+=buy*(double)qty*5;slotMinutes+=5;peak=Math.max(peak,cash);maxDd=Math.max(maxDd,peak-cash);}double hours=Math.max(1/60.0,(end-start)/3600.0);return new Result(trades,profit,profit/hours,trades==0?0:wins/(double)trades,maxDd,capitalMinutes/(Math.max(1,startingGp)*hours*60),slotMinutes/(hours*60*8));}
    private static double fillProbability(long offer,long low,long high,int qty,long volume){double spread=Math.max(1,high-low);double proximity=1-Math.min(1,Math.min(Math.abs(offer-low),Math.abs(high-offer))/(double)spread);double pressure=qty/(double)Math.max(1,volume);return Math.max(.05,Math.min(.98,.25+.55*proximity-.20*Math.min(1,pressure)));}
    private static List<Row> read(Path p)throws IOException{List<Row> out=new ArrayList<>();for(String line:Files.readAllLines(p)){if(line.isBlank()||line.toLowerCase(Locale.ROOT).startsWith("timestamp"))continue;String[] x=line.split(",");if(x.length<6)continue;try{out.add(new Row(Long.parseLong(x[0]),Integer.parseInt(x[1]),Long.parseLong(x[2]),Long.parseLong(x[3]),Long.parseLong(x[4]),Long.parseLong(x[5])));}catch(NumberFormatException ignored){}}out.sort(Comparator.comparingLong(Row::timestamp));return out;}
    record Row(long timestamp,int itemId,long high,long low,long highVolume,long lowVolume){}
    record Result(int trades,long realizedGp,double gpPerHour,double winRate,long maxDrawdown,double capitalUtilization,double slotUtilization){}
}
