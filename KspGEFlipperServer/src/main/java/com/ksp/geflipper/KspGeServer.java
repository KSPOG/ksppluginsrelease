package com.ksp.geflipper;

import com.ksp.geflipper.analytics.CalibrationService;
import com.ksp.geflipper.api.ApiServer;
import com.ksp.geflipper.candidates.CandidateEngine;
import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.dumps.DumpService;
import com.ksp.geflipper.executionmodel.*;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.optimizer.PortfolioOptimizer;
import com.ksp.geflipper.persistence.*;
import com.ksp.geflipper.portfolio.PortfolioService;
import com.ksp.geflipper.recommendations.RecommendationService;
import com.ksp.geflipper.repricing.ActionPolicy;
import com.ksp.geflipper.transactions.TransactionService;

public final class KspGeServer {
    private KspGeServer() {}
    public static void main(String[] args) throws Exception {
        ServerConfig config=ServerConfig.fromEnvironment();Store store=config.postgresEnabled()?new JdbcStore(config):new FileStore(config.dataDir());WikiMarketDataService market=new WikiMarketDataService(config,store);FeatureEngine features=new FeatureEngine();ForecastService forecasts=new ForecastService(store);ExecutionModel execution=new ExecutionModel(store);GeTaxService tax=new GeTaxService();CandidateEngine candidates=new CandidateEngine(config,market,features,forecasts,execution,tax);PortfolioService portfolio=new PortfolioService(store,market,tax,features,forecasts,execution);PortfolioOptimizer optimizer=new PortfolioOptimizer();ActionPolicy policy=new ActionPolicy(optimizer);RecommendationService recommendations=new RecommendationService(config,store,market,candidates,portfolio,policy,features);CalibrationService calibration=new CalibrationService(store);TransactionService transactions=new TransactionService(store,portfolio,calibration,market);DumpService dumps=new DumpService(config,store,market,features,forecasts);ApiServer api=new ApiServer(config,store,market,features,forecasts,recommendations,transactions,portfolio,dumps,calibration);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{api.close();dumps.close();market.close();store.close();}catch(Exception ignored){}}));market.start();dumps.start();api.start();Thread.currentThread().join();
    }
}
