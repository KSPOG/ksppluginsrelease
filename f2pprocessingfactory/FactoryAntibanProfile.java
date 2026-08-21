package net.runelite.client.plugins.microbot.f2pprocessingfactory;

/** Presets for factory-local humanization that only acts in safe idle windows. */
public enum FactoryAntibanProfile
{
    LIGHT(0.08,450,1_300,18,30,4_000,11_000,0.18,90_000,180_000,0.08,160,420,0.06),
    BALANCED(0.15,650,2_100,10,18,8_000,20_000,0.32,60_000,130_000,0.14,180,620,0.10),
    HIGH(0.24,850,3_000,7,13,12_000,30_000,0.48,45_000,100_000,0.22,220,850,0.14);

    final double shortPauseChance, moveMouseAwayChance, immediateCombinePauseChance, offerTimeoutJitterFraction;
    final int shortPauseMinMillis, shortPauseMaxMillis, longBreakBatchMin, longBreakBatchMax, longBreakMinMillis, longBreakMaxMillis,
        waitingMouseMinMillis, waitingMouseMaxMillis, immediateCombinePauseMinMillis, immediateCombinePauseMaxMillis;

    FactoryAntibanProfile(double a,int b,int c,int d,int e,int f,int g,double h,int i,int j,double k,int l,int m,double n) { shortPauseChance=a; shortPauseMinMillis=b; shortPauseMaxMillis=c; longBreakBatchMin=d; longBreakBatchMax=e; longBreakMinMillis=f; longBreakMaxMillis=g; moveMouseAwayChance=h; waitingMouseMinMillis=i; waitingMouseMaxMillis=j; immediateCombinePauseChance=k; immediateCombinePauseMinMillis=l; immediateCombinePauseMaxMillis=m; offerTimeoutJitterFraction=n; }
}
