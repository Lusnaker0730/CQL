package com.cqlplatform.service.ai;

import com.cqlplatform.model.CqlFixSuggestionResponse;
import com.cqlplatform.model.CqlTranslationResponse.CqlError;

public interface CqlFixService {
    CqlFixSuggestionResponse suggestFix(String cql, CqlError error);
    String getProviderName();
    String getModelName();
}
