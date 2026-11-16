package com.racetracker.mapper;

import com.racetracker.model.User;
import com.racetracker.service.LegalDocuments;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperLegalTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    void userWhoAcceptedTheCurrentDocumentsIsNotPrompted() {
        User user = User.builder()
            .privacyPolicyVersion(LegalDocuments.CURRENT_PRIVACY_POLICY_VERSION)
            .termsVersion(LegalDocuments.CURRENT_TERMS_VERSION)
            .build();

        assertThat(mapper.toDto(user).getLegalAcceptanceRequired()).isFalse();
    }

    @Test
    void userWithAMissingOrStaleAcceptanceIsPrompted() {
        User neverAccepted = User.builder().build();
        User staleTerms = User.builder()
            .privacyPolicyVersion(LegalDocuments.CURRENT_PRIVACY_POLICY_VERSION)
            .termsVersion("2020-01-01")
            .build();

        assertThat(mapper.toDto(neverAccepted).getLegalAcceptanceRequired()).isTrue();
        assertThat(mapper.toDto(staleTerms).getLegalAcceptanceRequired()).isTrue();
    }
}
