package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

/** Profile timing/probability values for the High Alch anti-ban controller. */
public enum KspHighAlchAntibanProfile {
    LIGHT(0,120,.015,.004,.012,3_250,4_300,3_500,8_000,300,520), BALANCED(10,220,.030,.009,.025,3_450,6_200,5_000,14_000,220,420), HEAVY(35,350,.055,.018,.045,3_900,8_500,8_000,22_000,140,300);
    final int castJitterMinMillis,castJitterMaxMillis,shortPauseMinMillis,shortPauseMaxMillis,longBreakMinMillis,longBreakMaxMillis,castsUntilBreakMin,castsUntilBreakMax; final double moveChance,offscreenChance,shortPauseChance;
    KspHighAlchAntibanProfile(int a,int b,double c,double d,double e,int f,int g,int h,int i,int j,int k){castJitterMinMillis=a;castJitterMaxMillis=b;moveChance=c;offscreenChance=d;shortPauseChance=e;shortPauseMinMillis=f;shortPauseMaxMillis=g;longBreakMinMillis=h;longBreakMaxMillis=i;castsUntilBreakMin=j;castsUntilBreakMax=k;}
}
