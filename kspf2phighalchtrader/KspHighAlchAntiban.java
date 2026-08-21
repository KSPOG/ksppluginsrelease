package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import java.util.concurrent.ThreadLocalRandom;

/** High-Alchemy-specific non-blocking anti-ban controller. */
public final class KspHighAlchAntiban {
    private long pauseUntil; private int castsUntilLongBreak,shortPauses,longBreaks; private String activity="Ready";
    public void reset(KspHighAlchAntibanProfile p){pauseUntil=shortPauses=longBreaks=0;activity="Ready";scheduleNextLongBreak(p);}
    public void disabled(){pauseUntil=0;castsUntilLongBreak=Integer.MAX_VALUE;activity="Off";}
    public boolean isPaused(){return System.currentTimeMillis()<pauseUntil;} public long remainingPauseMs(){return Math.max(0,pauseUntil-System.currentTimeMillis());}
    public int getShortPauses(){return shortPauses;} public int getLongBreaks(){return longBreaks;}
    public String getActivity(){if(isPaused())return activity+" ("+Math.max(1,remainingPauseMs()/1000)+"s)";if("Short pause".equals(activity)||"Long break".equals(activity))activity="Ready";return activity;}
    public int nextCastJitterMs(KspHighAlchAntibanProfile p){return randomInclusive(p.castJitterMinMillis,p.castJitterMaxMillis);}
    public void afterSuccessfulCast(KspHighAlchAntibanProfile p)
    {
        if(p==null)return; ThreadLocalRandom r=ThreadLocalRandom.current(); castsUntilLongBreak--;
        if(r.nextDouble()<p.moveChance){Rs2Antiban.moveMouseRandomly();activity="Mouse variation";}
        if(r.nextDouble()<p.offscreenChance){Rs2Antiban.moveMouseOffScreen();activity="Mouse off-screen";}
        if(r.nextDouble()<p.shortPauseChance){pauseUntil=Math.max(pauseUntil,System.currentTimeMillis()+randomInclusive(p.shortPauseMinMillis,p.shortPauseMaxMillis));shortPauses++;activity="Short pause";}
        if(castsUntilLongBreak<=0){pauseUntil=Math.max(pauseUntil,System.currentTimeMillis()+randomInclusive(p.longBreakMinMillis,p.longBreakMaxMillis));longBreaks++;activity="Long break";Rs2Antiban.moveMouseOffScreen();scheduleNextLongBreak(p);}
    }
    private void scheduleNextLongBreak(KspHighAlchAntibanProfile p){castsUntilLongBreak=randomInclusive(p.castsUntilBreakMin,p.castsUntilBreakMax);}
    private static int randomInclusive(int min,int max){return ThreadLocalRandom.current().nextInt(min,max+1);}
}
