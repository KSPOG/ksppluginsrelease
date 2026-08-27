package com.ksp.geflipper.recommendations;

import com.ksp.geflipper.candidates.CandidateEngine;
import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import com.ksp.geflipper.portfolio.PortfolioService;
import com.ksp.geflipper.repricing.ActionPolicy;

import java.time.*;
import java.util.*;

public final class RecommendationService {
    private final ServerConfig config;private final Store store;private final WikiMarketDataService market;private final CandidateEngine candidates;private final PortfolioService portfolio;private final ActionPolicy policy;private final FeatureEngine features;
    public RecommendationService(ServerConfig config,Store store,WikiMarketDataService market,CandidateEngine candidates,PortfolioService portfolio,ActionPolicy policy,FeatureEngine features){this.config=config;this.store=store;this.market=market;this.candidates=candidates;this.portfolio=portfolio;this.policy=policy;this.features=features;}

    public TradeSuggestion recommend(AccountState raw){
        AccountState account=new AccountState(raw.accountKey(),raw.worldMember(),raw.accountMember(),raw.f2pOnly(),raw.totalGeSlots(),raw.maxPluginSlots(),raw.gp(),raw.inventory(),raw.bank(),raw.uncollected(),raw.otherStorage(),raw.offers(),raw.blockedItems(),raw.blockedItemNames(),raw.allowedItemNames(),raw.strategy(),portfolio.snapshot(raw.accountKey()));store.saveAccount(account);store.saveOffers(account.accountKey(),account.offers());
        if(!market.ready())return TradeSuggestion.waitSuggestion("Market data is unavailable or older than the configured hard freshness limit. Last error: "+market.lastError());
        List<FlipCandidate> entries=candidates.generate(account),exits=portfolio.exitCandidates(account);TradeSuggestion suggestion=policy.decide(account,entries,exits);
        if(suggestion.slot()>=0&&(suggestion.type()==SuggestionType.MODIFY_BUY||suggestion.type()==SuggestionType.MODIFY_SELL||suggestion.type()==SuggestionType.ABORT)){
            for(GeOfferState offer:account.offers()){
                if(offer.active()&&offer.slot()==suggestion.slot()&&offer.suggestionId()!=null){
                    store.markRecommendationStatus(offer.suggestionId(),suggestion.type()==SuggestionType.ABORT?"ABORTED":"MODIFIED");
                    break;
                }
            }
        }
        long age=market.snapshot(suggestion.itemId()).map(s->Math.max(Duration.between(s.latestHighTime(),Instant.now()).toSeconds(),Duration.between(s.latestLowTime(),Instant.now()).toSeconds())).orElse(0L);
        suggestion=new TradeSuggestion(suggestion.id(),suggestion.type(),suggestion.candidateType(),suggestion.slot(),suggestion.itemId(),suggestion.name(),suggestion.price(),suggestion.exitPrice(),suggestion.quantity(),suggestion.expectedProfit(),suggestion.expectedDurationSeconds(),suggestion.expectedGpPerHour(),suggestion.confidence(),suggestion.hold(),suggestion.explanation(),suggestion.generatedAt(),age);
        final int suggestionItemId = suggestion.itemId();
        Map<String,Double> snapshotFeatures=market.snapshot(suggestionItemId).map(s->features.asMap(features.extract(s,market.history(suggestionItemId,512)))).orElse(Map.of());store.saveRecommendation(account.accountKey(),suggestion,snapshotFeatures,account.strategy());return suggestion;
    }
}
