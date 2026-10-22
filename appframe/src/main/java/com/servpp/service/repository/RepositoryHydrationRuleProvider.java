package com.servpp.service.repository;

import java.util.List;

import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;

public interface RepositoryHydrationRuleProvider {
    List<HydrationRule> getRepositoryHydrationRules();
}
