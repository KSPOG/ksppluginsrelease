package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Side panel for recommendation detail, probabilistic forecasts, portfolio, and model metrics. */
final class KspGEFlipperPanel extends PluginPanel {
    private final KspGEFlipperConfig config;
    private final JTextArea suggestion = textArea();
    private final JTextArea portfolio = textArea();
    private final JTextArea analytics = textArea();
    private final ForecastGraph forecastGraph = new ForecastGraph();
    private final JLabel forecastTitle = new JLabel("No server forecast selected", SwingConstants.CENTER);
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ksp-ge-panel-refresh"); t.setDaemon(true); return t;
    });
    private final Timer localTimer;
    private volatile boolean closed;

    KspGEFlipperPanel(KspGEFlipperConfig config) {
        super(false);
        this.config = config;
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Suggestion", scroll(suggestion));
        tabs.addTab("Forecast", forecastTab());
        tabs.addTab("Portfolio", scroll(portfolio));
        tabs.addTab("Analytics", scroll(analytics));
        add(tabs, BorderLayout.CENTER);

        localTimer = new Timer(1_000, e -> refreshSuggestion());
        localTimer.start();
        worker.scheduleWithFixedDelay(this::refreshServerData, 1, 5, TimeUnit.SECONDS);
        refreshSuggestion();
    }

    void shutdown() {
        closed = true;
        localTimer.stop();
        worker.shutdownNow();
    }

    private JPanel forecastTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        forecastGraph.setPreferredSize(new Dimension(235, 260));
        panel.add(forecastTitle, BorderLayout.NORTH);
        panel.add(forecastGraph, BorderLayout.CENTER);
        return panel;
    }

    private void refreshSuggestion() {
        StringBuilder b = new StringBuilder();
        b.append("Engine: ").append(KspGEFlipperRuntime.engine).append('\n');
        b.append("Backend: ").append(KspGEFlipperRuntime.backend).append('\n');
        b.append("Action: ").append(KspGEFlipperScript.candidateType).append('\n');
        b.append("Item: ").append(KspGEFlipperScript.bestCandidate).append('\n');
        b.append("Buy: ").append(KspGEFlipperScript.candidateBuy).append('\n');
        b.append("Sell: ").append(KspGEFlipperScript.candidateSell).append('\n');
        b.append("Quantity: ").append(KspGEFlipperScript.candidateQty).append('\n');
        b.append("Expected profit: ").append(KspGEFlipperScript.candidateProfit).append('\n');
        b.append("Expected duration: ").append(KspGEFlipperScript.candidateExpectedMinutes).append("m\n");
        b.append("Expected GP/h: ").append(KspGEFlipperScript.candidateGpPerHour).append('\n');
        b.append("Confidence: ").append(String.format("%.1f%%", KspGEFlipperScript.candidateConfidence * 100.0)).append("\n\n");
        b.append("Reason\n").append(KspGEFlipperRuntime.explanation).append("\n\n");
        b.append("Dump stream: ").append(KspGEFlipperRuntime.dump);
        suggestion.setText(b.toString());
    }

    private void refreshServerData() {
        if (closed || !KspGEFlipperRuntime.engine.startsWith("Server")) return;
        try {
            KspGEFlipperBackendClient client = new KspGEFlipperBackendClient(config.backendUrl(), config.backendApiKey());
            String account = URLEncoder.encode(KspGEFlipperRuntime.accountKey, StandardCharsets.UTF_8);
            JsonObject portfolioJson = client.json("/v1/portfolio?account=" + account);
            JsonObject metricJson = client.json("/v1/metrics");
            String portfolioText = portfolioSummary(portfolioJson);
            String metricText = metricSummary(metricJson);

            JsonObject priceJson = null;
            int itemId = KspGEFlipperRuntime.itemId;
            if (itemId > 0) priceJson = client.json("/v1/prices/" + itemId + "?timeframe=" + Math.max(5, config.timeframeMinutes()));
            JsonObject finalPriceJson = priceJson;
            SwingUtilities.invokeLater(() -> {
                portfolio.setText(portfolioText);
                analytics.setText(metricText);
                if (finalPriceJson != null) {
                    forecastTitle.setText(KspGEFlipperScript.bestCandidate + " forecast");
                    forecastGraph.update(finalPriceJson);
                }
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> analytics.setText("Backend panel refresh unavailable:\n" + e.getMessage()));
        }
    }

    private static String portfolioSummary(JsonObject root) {
        StringBuilder b = new StringBuilder();
        b.append("Realized P&L: ").append(longValue(root,"realizedProfit")).append('\n');
        b.append("Unrealized P&L: ").append(longValue(root,"unrealizedProfit")).append("\n\nPositions\n");
        JsonArray positions = array(root,"positions");
        if (positions.size() == 0) b.append("None");
        for (JsonElement element : positions) {
            if (!element.isJsonObject()) continue;
            JsonObject p = element.getAsJsonObject();
            b.append('#').append(intValue(p,"itemId")).append(' ')
                    .append(text(p,"status")).append("  ")
                    .append(intValue(p,"closedQuantity")).append('/')
                    .append(intValue(p,"openQuantity")).append(" sold\n")
                    .append("  cost=").append(longValue(p,"totalBuyCost"))
                    .append(" revenue=").append(longValue(p,"totalSellRevenue"))
                    .append(" tax=").append(longValue(p,"taxPaid")).append('\n');
        }
        return b.toString();
    }

    private static String metricSummary(JsonObject root) {
        JsonObject c = object(root,"calibration");
        StringBuilder b = new StringBuilder("Calibration\n");
        b.append("Outcome samples: ").append(longValue(c,"samples")).append('\n');
        b.append("Duration MAE: ").append(String.format("%.1fs", doubleValue(c,"durationMaeSeconds"))).append('\n');
        b.append("Duration MAPE: ").append(String.format("%.1f%%", doubleValue(c,"durationMape")*100)).append('\n');
        b.append("Profit MAE: ").append(String.format("%.1f", doubleValue(c,"profitMae"))).append('\n');
        b.append("Forecast MAE: ").append(String.format("%.1f", doubleValue(c,"forecastMae"))).append('\n');
        b.append("IQR coverage: ").append(String.format("%.1f%%", doubleValue(c,"iqrCoverage")*100)).append('\n');
        b.append("Acceptance: ").append(String.format("%.1f%%", doubleValue(c,"recommendationAcceptanceRate")*100)).append('\n');
        b.append("Modify rate: ").append(String.format("%.1f%%", doubleValue(c,"modifyRate")*100)).append('\n');
        b.append("Abort rate: ").append(String.format("%.1f%%", doubleValue(c,"abortRate")*100));
        return b.toString();
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        return area;
    }
    private static JScrollPane scroll(Component component) { return new JScrollPane(component); }
    private static JsonObject object(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static JsonArray array(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static String text(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    private static long longValue(JsonObject o,String k){try{return o.get(k).getAsLong();}catch(Exception ignored){return 0;}}
    private static int intValue(JsonObject o,String k){try{return o.get(k).getAsInt();}catch(Exception ignored){return 0;}}
    private static double doubleValue(JsonObject o,String k){try{return o.get(k).getAsDouble();}catch(Exception ignored){return 0;}}

    private static final class ForecastGraph extends JPanel {
        private final List<P> historyLow = new ArrayList<>(), historyHigh = new ArrayList<>(), forecastLow = new ArrayList<>(), forecastHigh = new ArrayList<>();

        void update(JsonObject root) {
            historyLow.clear(); historyHigh.clear(); forecastLow.clear(); forecastHigh.clear();
            JsonArray history = array(root,"history");
            int start = Math.max(0, history.size()-80);
            for(int i=start;i<history.size();i++){
                JsonObject p=history.get(i).getAsJsonObject();
                historyLow.add(new P(i-start,doubleValue(p,"lowPrice"),0,0));
                historyHigh.add(new P(i-start,doubleValue(p,"highPrice"),0,0));
            }
            JsonObject forecast=object(root,"forecast");
            readForecast(array(forecast,"low"),forecastLow);
            readForecast(array(forecast,"high"),forecastHigh);
            repaint();
        }

        private static void readForecast(JsonArray a,List<P> out){
            for(int i=0;i<a.size();i++){JsonObject p=a.get(i).getAsJsonObject();out.add(new P(i,doubleValue(p,"mean"),doubleValue(p,"q25"),doubleValue(p,"q75")));}
        }

        @Override protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g=(Graphics2D)raw.create();
            try {
                int w=getWidth(),h=getHeight(),pad=20;
                if(w<=2*pad||h<=2*pad)return;
                double[] bounds=priceBounds(); if(bounds==null)return;
                g.setColor(getForeground());
                g.drawRect(pad,pad,w-2*pad,h-2*pad);
                drawLine(g,historyLow,pad,w,h,bounds,0.45f);
                drawLine(g,historyHigh,pad,w,h,bounds,0.70f);
                drawBand(g,forecastLow,pad,w,h,bounds,0.10f);
                drawBand(g,forecastHigh,pad,w,h,bounds,0.10f);
                drawLine(g,forecastLow,pad,w,h,bounds,1.0f);
                drawLine(g,forecastHigh,pad,w,h,bounds,1.0f);
            } finally { g.dispose(); }
        }

        private double[] priceBounds(){
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(List<P> list:List.of(historyLow,historyHigh,forecastLow,forecastHigh))for(P p:list){double lo=p.q25>0?p.q25:p.mean,hi=p.q75>0?p.q75:p.mean;min=Math.min(min,lo);max=Math.max(max,hi);}
            return Double.isFinite(min)&&max>min?new double[]{min,max}:null;
        }
        private void drawLine(Graphics2D g,List<P> list,int pad,int w,int h,double[] b,float alpha){
            if(list.size()<2)return;Composite old=g.getComposite();g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));int n=list.size();
            for(int i=1;i<n;i++){int x1=pad+(i-1)*(w-2*pad)/Math.max(1,n-1),x2=pad+i*(w-2*pad)/Math.max(1,n-1);g.drawLine(x1,y(list.get(i-1).mean,pad,h,b),x2,y(list.get(i).mean,pad,h,b));}g.setComposite(old);
        }
        private void drawBand(Graphics2D g,List<P> list,int pad,int w,int h,double[] b,float alpha){
            if(list.size()<2)return;Polygon poly=new Polygon();int n=list.size();for(int i=0;i<n;i++)poly.addPoint(pad+i*(w-2*pad)/Math.max(1,n-1),y(list.get(i).q75,pad,h,b));for(int i=n-1;i>=0;i--)poly.addPoint(pad+i*(w-2*pad)/Math.max(1,n-1),y(list.get(i).q25,pad,h,b));Composite old=g.getComposite();g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));g.fillPolygon(poly);g.setComposite(old);
        }
        private static int y(double v,int pad,int h,double[] b){return pad+(int)Math.round((b[1]-v)/(b[1]-b[0])*(h-2*pad));}
        private static final class P { final int x;final double mean,q25,q75;P(int x,double mean,double q25,double q75){this.x=x;this.mean=mean;this.q25=q25;this.q75=q75;} }
    }
}
